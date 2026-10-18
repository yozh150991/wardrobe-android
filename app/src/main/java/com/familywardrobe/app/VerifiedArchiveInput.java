package com.familywardrobe.app;
import java.io.*;
import java.security.*;
import java.util.Locale;

/** Fingerprint and byte bound cover the exact stream inspected by ZIP validation, including its tail. */
public final class VerifiedArchiveInput extends FilterInputStream {
    private final MessageDigest hash;
    private final long maximum;
    private final Runnable checkpoint;
    private long count;
    private String fingerprint;
    public VerifiedArchiveInput(InputStream source, long maximum, Runnable checkpoint) {
        super(source); if (maximum < 1) throw new IllegalArgumentException();
        this.maximum=maximum; this.checkpoint=checkpoint;
        try { hash=MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public int read() throws IOException {
        checkpoint.run(); int b=in.read(); if (b>=0) { bounded(1); hash.update((byte)b); } return b;
    }
    @Override public int read(byte[] bytes,int offset,int length) throws IOException {
        checkpoint.run(); int n=in.read(bytes,offset,length); if (n>0) { bounded(n); hash.update(bytes,offset,n); } return n;
    }
    @Override public long skip(long amount) throws IOException {
        if (amount <= 0) return 0;
        byte[] buffer = new byte[(int)Math.min(amount, 64*1024)];
        long consumed = 0;
        while (consumed < amount) {
            int n = read(buffer, 0, (int)Math.min(buffer.length, amount-consumed));
            if (n < 0) break;
            if (n == 0) { if (read() < 0) break; consumed++; } else consumed += n;
        }
        return consumed;
    }
    @Override public boolean markSupported() { return false; }
    @Override public void mark(int limit) {}
    @Override public void reset() throws IOException { throw new IOException("Archive stream cannot rewind"); }
    private void bounded(int n) throws IOException { count+=n; if (count>maximum) throw new IOException("Archive size limit"); }
    public String finish() throws IOException {
        if (fingerprint != null) return fingerprint;
        byte[] buffer=new byte[64*1024]; while (read(buffer,0,buffer.length)>=0) {}
        StringBuilder result=new StringBuilder(64); for(byte b:hash.digest()) result.append(String.format(Locale.ROOT,"%02x",b&255));
        fingerprint=result.toString(); return fingerprint;
    }
}
