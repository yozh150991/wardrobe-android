"""Bounded ZIP validation; nothing is extracted to a client-provided path."""
import hashlib
import io
import stat
import warnings
import zipfile
import zlib
from PIL import Image
from .errors import FamilyError, require
from .schema import integer, strict_json, validate_snapshot

MAX_ARCHIVE = 128 * 1024 * 1024
MAX_EXPANDED = 256 * 1024 * 1024
MAX_MANIFEST = 8 * 1024 * 1024
MAX_PHOTO = 12 * 1024 * 1024
MAX_ENTRIES = 5001
MAX_PIXELS = 16 * 1024 * 1024


def validate_archive(data, expected, base_revision, checksum):
    require(0 < len(data) <= MAX_ARCHIVE, 'Archive size limit')
    require(hashlib.sha256(data).hexdigest() == checksum, 'Archive checksum mismatch')
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entries = archive.infolist()
            require(1 <= len(entries) <= MAX_ENTRIES)
            require(sum(e.file_size for e in entries) <= MAX_EXPANDED, 'Expanded archive size limit')
            names = set()
            for e in entries:
                require(e.filename == e.orig_filename and e.filename not in names, 'Duplicate or invalid ZIP entry')
                names.add(e.filename)
                mode = e.external_attr >> 16
                require(not e.is_dir() and (stat.S_IFMT(mode) in (0, stat.S_IFREG)), 'Non-file ZIP entry')
                require(not e.flag_bits & 1 and e.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED))
                require(0 < e.file_size <= (MAX_MANIFEST if e.filename == 'manifest.json' else MAX_PHOTO))
            require('manifest.json' in names)
            env = strict_json(archive.read('manifest.json').decode('utf-8'))
            require(type(env) is dict and set(env) == {'format','version','remoteId','wardrobeId','ownerUid','baseRevision','snapshot'})
            require(env['format'] == 'family-wardrobe-scoped' and type(env['version']) is int and env['version'] == 1)
            require(integer(env['baseRevision']) and env['baseRevision'] == base_revision)
            require(all(env[k] == expected[k] for k in ('remoteId','wardrobeId','ownerUid')), 'Archive identity mismatch')
            counts = validate_snapshot(env['snapshot'], expected['wardrobeId'])
            photo_files = env['snapshot']['photoFiles']
            require(names == {'manifest.json'} | {f'photos/{i}.{ext}' for i, ext in photo_files.items()}, 'Unexpected ZIP files')
            for i, ext in photo_files.items():
                photo = archive.read(f'photos/{i}.{ext}')  # zipfile verifies CRC at EOF
                with warnings.catch_warnings():
                    warnings.simplefilter('error', Image.DecompressionBombWarning)
                    with Image.open(io.BytesIO(photo)) as image:
                        require(image.format == ('PNG' if ext == 'png' else 'JPEG'), 'Photo format mismatch')
                        require(0 < image.width * image.height <= MAX_PIXELS and max(image.size) <= 8192)
                        require(getattr(image, 'n_frames', 1) == 1, 'Animated photo is unsupported')
                        image.verify()
                    with Image.open(io.BytesIO(photo)) as image:
                        image.load()  # verifies actual decodability, not just the header
            return counts
    except FamilyError:
        raise
    except (ValueError, KeyError, TypeError, OSError, RuntimeError, zlib.error, zipfile.BadZipFile,
            Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
        raise FamilyError('invalid-argument', 'Invalid family archive') from exc
