package com.familywardrobe.app;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Executes the production extractor and lease, including adversarial archive bytes. */
public final class FamilyCacheCases {
    private static int assertions;
    private static final Runnable CHECK = () -> {};
    private static final String PHOTO = "photos/00000000-0000-0000-0000-000000000001.png";
    private static void ok(boolean value) { assertions++; if (!value) throw new AssertionError("case " + assertions); }
    private interface Run { void run() throws Exception; }
    private static void fails(Run run) throws Exception {
        assertions++; try { run.run(); } catch (IOException | IllegalArgumentException | java.util.concurrent.CancellationException expected) { return; }
        throw new AssertionError("Expected rejection " + assertions);
    }
    private static File zip(File root, Map<String, byte[]> entries) throws Exception {
        File file = new File(root, UUID.randomUUID()+".zip");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(e.getKey())); output.write(e.getValue()); output.closeEntry();
            }
        }
        return file;
    }
    private static FamilyCacheZip.Extracted extract(File file, File root) throws Exception {
        return FamilyCacheZip.extract(file, file.length(), FamilyCacheZip.hash(file,CHECK), new File(root,UUID.randomUUID().toString()), CHECK);
    }
    private static void replace(byte[] bytes, byte[] from, byte[] to) {
        for (int i=0;i<=bytes.length-from.length;i++) {
            boolean match=true; for(int j=0;j<from.length;j++) if(bytes[i+j]!=from[j]) {match=false;break;}
            if(match) {System.arraycopy(to,0,bytes,i,to.length);i+=from.length-1;}
        }
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("family-cache-cases").toFile();
        try {
            byte[] manifest="{\"format\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            File valid=zip(root,Map.of("manifest.json",manifest,PHOTO,new byte[]{1,2,3}));
            FamilyCacheZip.Extracted parsed=extract(valid,root);
            ok(parsed.manifest.equals(new String(manifest,StandardCharsets.UTF_8)));
            ok(parsed.names.equals(Set.of("manifest.json",PHOTO)));
            fails(()->FamilyCacheZip.extract(valid,valid.length()+1,FamilyCacheZip.hash(valid,CHECK),new File(root,"size"),CHECK));
            fails(()->FamilyCacheZip.extract(valid,valid.length(),"00".repeat(32),new File(root,"hash"),CHECK));
            fails(()->FamilyCacheZip.extract(valid,valid.length(),"bad",new File(root,"bad-hash"),CHECK));
            fails(()->FamilyCacheZip.extract(valid,valid.length(),FamilyCacheZip.hash(valid,CHECK),root,CHECK));
            File missing=zip(root,Map.of(PHOTO,new byte[]{1}));fails(()->extract(missing,root));
            for(String name:List.of("../outside", "/absolute", "photos/../outside", "photos/", "manifest.JSON", "private.db", "photos/00000000-0000-0000-0000-000000000001.webp")) {
                File bad=zip(root,Map.of("manifest.json",manifest,name,new byte[]{1}));fails(()->extract(bad,root));
            }
            File invalidUtf8=zip(root,Map.of("manifest.json",new byte[]{(byte)0xc3,0x28}));fails(()->extract(invalidUtf8,root));
            File bigManifest=zip(root,Map.of("manifest.json",new byte[FamilyCacheZip.MAX_MANIFEST+1]));fails(()->extract(bigManifest,root));
            File bigPhoto=zip(root,Map.of("manifest.json",manifest,PHOTO,new byte[(int)FamilyCacheZip.MAX_PHOTO+1]));fails(()->extract(bigPhoto,root));
            File emptyPhoto=zip(root,Map.of("manifest.json",manifest,PHOTO,new byte[0]));fails(()->extract(emptyPhoto,root));
            File duplicate=zip(root,Map.of("manifest.json",manifest,"manifesz.json",manifest));
            byte[] bytes=Files.readAllBytes(duplicate.toPath());replace(bytes,"manifesz.json".getBytes(StandardCharsets.US_ASCII),"manifest.json".getBytes(StandardCharsets.US_ASCII));Files.write(duplicate.toPath(),bytes);fails(()->extract(duplicate,root));
            File crc=zip(root,Map.of("manifest.json",manifest));bytes=Files.readAllBytes(crc.toPath());
            for(int i=0;i<bytes.length-20;i++) if(bytes[i]==0x50 && bytes[i+1]==0x4b && bytes[i+2]==1 && bytes[i+3]==2) {bytes[i+16]^=1;break;}
            Files.write(crc.toPath(),bytes);fails(()->extract(crc,root));
            File truncated=zip(root,Map.of("manifest.json",manifest));try(RandomAccessFile file=new RandomAccessFile(truncated,"rw")){file.setLength(file.length()-20);}fails(()->extract(truncated,root));
            File tooMany=new File(root,"count.zip");
            try(ZipOutputStream output=new ZipOutputStream(new FileOutputStream(tooMany))) {
                output.putNextEntry(new ZipEntry("manifest.json"));output.write(manifest);output.closeEntry();
                for(int i=0;i<=FamilyCacheZip.MAX_PHOTOS;i++){output.putNextEntry(new ZipEntry("photos/"+UUID.randomUUID()+".png"));output.write(1);output.closeEntry();}
            }
            fails(()->extract(tooMany,root));
            File expanded=new File(root,"expanded.zip");byte[] chunk=new byte[1024*1024];
            try(ZipOutputStream output=new ZipOutputStream(new FileOutputStream(expanded))) {
                output.putNextEntry(new ZipEntry("manifest.json"));output.write(manifest);output.closeEntry();
                for(int i=0;i<22;i++){output.putNextEntry(new ZipEntry("photos/"+UUID.randomUUID()+".png"));for(int j=0;j<12;j++)output.write(chunk);output.closeEntry();}
            }
            fails(()->extract(expanded,root));
            ByteArrayOutputStream out=new ByteArrayOutputStream();fails(()->FamilyCacheZip.copy(new ByteArrayInputStream(new byte[4]),out,3,CHECK));ok(out.size()==0);
            ok(FamilyCacheZip.copy(new ByteArrayInputStream(new byte[3]),out,3,CHECK)==3);
            final int[] ticks={0};fails(()->FamilyCacheZip.extract(valid,valid.length(),FamilyCacheZip.hash(valid,CHECK),new File(root,"cancel"),()->{if(++ticks[0]>3)throw new java.util.concurrent.CancellationException();}));
            FamilyReadLease lease=new FamilyReadLease();ok(!lease.permits("a",0));
            for(FamilyRules.Role role:FamilyRules.Role.values()) {
                lease.grant("a",role,100,200);ok(lease.permits("a",100));ok(lease.permits("a",100+FamilyRules.CACHE_LEASE_MS-1));
                ok(!lease.permits("a",100+FamilyRules.CACHE_LEASE_MS));ok(!lease.permits("b",200));ok(!lease.permits("a",99));
            }
            lease.clear();ok(!lease.permits("a",200));
            fails(()->lease.grant("a",FamilyRules.Role.VIEWER,0,FamilyRules.CACHE_LEASE_MS));ok(!lease.permits("a",0));
            fails(()->lease.grant("",FamilyRules.Role.OWNER,0,0));
            fails(()->lease.grant("a",null,0,0));
            fails(()->lease.grant("a",FamilyRules.Role.VIEWER,100,99));
            System.out.println(assertions+" production family ZIP/lease assertions passed");
        } finally { try(var paths=Files.walk(root.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});} }
    }
}
