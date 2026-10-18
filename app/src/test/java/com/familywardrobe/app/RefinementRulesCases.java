package com.familywardrobe.app;
import java.util.*;
import java.io.*;
import java.security.*;
import java.util.zip.*;

/** Executes production code on a host JDK; does not simulate Compose or Room. */
public final class RefinementRulesCases {
    private static int checks;
    private static void check(boolean yes,String message) { checks++; if(!yes) throw new AssertionError(message); }
    private static void close(float a,float b) { check(Math.abs(a-b)<.0001f,"geometry "+a+" != "+b); }
    private static DailyRules.Item item(String id,String category,String season,String availability,String last) {
        return new DailyRules.Item(new WardrobeRules.Piece(id,"home",category,availability,false,season,"casual","navy"),0,false,last);
    }
    private static DailyDiagnostics.Reason reason(List<DailyRules.Item> items,String weather,Set<String> scope,Set<String> full,boolean packed,Set<String> excluded,int days,Set<String> rejected,Map<String,String> fixed) {
        return DailyDiagnostics.explain(items,"home","everyday",weather,"2026-10-02","test",scope,full,packed,excluded,days,rejected,fixed);
    }
    private static DailyDiagnostics.Reason reason(List<DailyRules.Item> items) { return reason(items,"any_weather",null,null,false,Set.of(),0,Set.of(),Map.of()); }
    private static void diagnostics() {
        List<DailyRules.Item> base=List.of(item("dress","dress","all","available",""),item("shoes","shoes","all","available",""));
        check(reason(List.of())==DailyDiagnostics.Reason.NO_ITEMS,"empty wardrobe");
        check(reason(base,"any_weather",Set.of(),Set.of(),false,Set.of(),0,Set.of(),Map.of())==DailyDiagnostics.Reason.CAPSULE_EMPTY,"empty capsule");
        check(reason(base,"any_weather",Set.of("dress"),Set.of("dress","shoes"),true,Set.of(),0,Set.of(),Map.of())==DailyDiagnostics.Reason.PACKED,"unpacked shoes");
        check(reason(List.of(base.get(0)))==DailyDiagnostics.Reason.SHOES,"missing shoes");
        check(reason(List.of(base.get(1)))==DailyDiagnostics.Reason.BASE,"missing flexible base");
        check(reason(List.of(base.get(0),item("shoes","shoes","all","laundry","")))==DailyDiagnostics.Reason.UNAVAILABLE,"laundry is not missing ownership");
        check(reason(base,"cold",null,null,false,Set.of(),0,Set.of(),Map.of())==DailyDiagnostics.Reason.WEATHER,"cold needs outerwear");
        check(reason(List.of(item("dress","dress","summer","available",""),item("shoes","shoes","winter","available","")))==DailyDiagnostics.Reason.SEASONS,"incompatible seasons");
        List<DailyRules.Item> recentlyWorn=List.of(item("dress","dress","all","available","2026-10-01"),base.get(1));
        check(reason(recentlyWorn,"any_weather",null,null,false,Set.of(),7,Set.of(),Map.of())==DailyDiagnostics.Reason.REPEATS,"repeat rule is actionable");
        check(reason(base,"any_weather",null,null,false,Set.of("dress"),0,Set.of(),Map.of())==DailyDiagnostics.Reason.EXCLUDED,"excluded dress");
        check(reason(base,"any_weather",null,null,false,Set.of(),0,Set.of("dress|shoes"),Map.of())==DailyDiagnostics.Reason.FEEDBACK,"hidden look");
        List<DailyRules.Item> pinned=new ArrayList<>(base);pinned.add(item("winter","dress","winter","available",""));
        check(reason(pinned,"warm",null,null,false,Set.of(),0,Set.of(),Map.of("dress","winter"))==DailyDiagnostics.Reason.PINS,"weather-incompatible pin");
        check(reason(recentlyWorn,"any_weather",null,null,false,Set.of("shoes"),7,Set.of(),Map.of())==DailyDiagnostics.Reason.CONSTRAINTS,"two independent constraints are not falsely attributed to one");
        List<DailyRules.Item> scoped=new ArrayList<>(base);scoped.add(item("foreign","dress","all","available",""));
        check(reason(scoped,"any_weather",Set.of("shoes"),Set.of("shoes"),false,Set.of(),0,Set.of(),Map.of())==DailyDiagnostics.Reason.BASE,"scope never expands silently");
        for(String category:List.of("dress","jumpsuit")) {
            List<DailyRules.Item> items=List.of(item("one",category,"all","available",""),base.get(1));
            check(!DailyRules.generate(items,"home","everyday","any_weather","2026-10-02","test",Set.of(),Map.of(),3).isEmpty(),"flexible first look "+category);
        }
        check(DailyRules.generate(recentlyWorn.stream().filter(i->RecommendationRules.allowed(false,i.lastWear,"2026-10-02",7)).toList(),"home","everyday","any_weather","2026-10-02","test",Set.of(),Map.of(),3).isEmpty(),"diagnosis does not mutate strict generation");
    }
    private static void geometry() {
        Random random=new Random(10402);
        for(int n=0;n<300;n++) {
            int w=100+random.nextInt(1900),h=100+random.nextInt(1900);float vw=240+random.nextInt(500),vh=260+random.nextInt(400);
            float zoom=1+random.nextFloat()*7;
            float[] f=PhotoViewportRules.viewport(w,h,vw,vh,zoom,(random.nextFloat()-.5f)*6000,(random.nextFloat()-.5f)*6000);
            float x=random.nextFloat(),y=random.nextFloat();float[] p=PhotoViewportRules.point(f[0]+x*f[2],f[1]+y*f[3],f,false);
            check(p!=null,"image points map at zoom "+zoom);close(x,p[0]);close(y,p[1]);
            float[] outside=PhotoViewportRules.point(f[0]-10,f[1]-10,f,false);check(outside==null,"outside drag start ignored");
            float[] edge=PhotoViewportRules.point(f[0]-10,f[1]+f[3]+10,f,true);close(edge[0],0);close(edge[1],1);
            if(f[2]>=vw) check(f[0]<=.001f && f[0]+f[2]>=vw-.001f,"no horizontal gap while panning");
            if(f[3]>=vh) check(f[1]<=.001f && f[1]+f[3]>=vh-.001f,"no vertical gap while panning");
            int corner=n%4;float[] crop=PhotoViewportRules.resize(.1f,.1f,.9f,.9f,corner,x*2-1,y*2-1);
            int[] pixels=PhotoGeometry.crop(w,h,crop[0],crop[1],crop[2],crop[3]);
            check(pixels[2]>=1 && pixels[3]>=1 && crop[2]-crop[0]>=.04999f && crop[3]-crop[1]>=.04999f,"resize always valid and accessible via shared bounds");
        }
        for(float zoom:new float[]{0,9,Float.NaN}) {try {PhotoViewportRules.viewport(100,100,300,300,zoom,0,0);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
        int[] pixels=new int[64*64];Arrays.fill(pixels,0xff235678);int[] before=pixels.clone();
        List<PhotoMaskRules.Stroke> strokes=List.of(new PhotoMaskRules.Stroke(.2f,.5f,.7f,.5f,.02f,false));
        int[] count={0};try {PhotoMaskRules.paint(pixels,64,64,strokes,()->{if(++count[0]>=2)throw new java.util.concurrent.CancellationException();});throw new AssertionError();}
        catch(java.util.concurrent.CancellationException expected) {check(Arrays.equals(before,pixels),"cancellation never edits source pixels");}
        int[] edited=PhotoMaskRules.paint(pixels,64,64,strokes);for(int i=0;i<pixels.length;i++)check((pixels[i]&0xffffff)==(edited[i]&0xffffff),"mask preserves RGB after zoom geometry");
    }
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void archive() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {zip.putNextEntry(new ZipEntry("manifest.json"));zip.write("{\"version\":7}".getBytes());zip.closeEntry();zip.putNextEntry(new ZipEntry("photos/test.png"));zip.write(new byte[200000]);zip.closeEntry();}
        byte[] bytes=output.toByteArray();
        for(int buffer:new int[]{8,512,65536}) {
            VerifiedArchiveInput checked=new VerifiedArchiveInput(new ByteArrayInputStream(bytes),bytes.length,()->{});
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(checked,buffer))) {while(zip.getNextEntry()!=null){zip.transferTo(OutputStream.nullOutputStream());zip.closeEntry();}check(checked.finish().equals(hash(bytes)),"ZIP fingerprint includes tail with buffer "+buffer);check(checked.finish().equals(hash(bytes)),"finish is idempotent");}
        }
        byte[] changed=bytes.clone();changed[changed.length-1]^=1;check(!hash(changed).equals(hash(bytes)),"same-sized change is detected");
        try(VerifiedArchiveInput checked=new VerifiedArchiveInput(new ByteArrayInputStream(bytes),bytes.length,()->{})) {
            check(checked.skip(29)==29,"skipped bytes remain part of validation");
            check(checked.finish().equals(hash(bytes)),"fingerprint covers skipped bytes and remaining tail");
            check(!checked.markSupported(),"digest stream cannot rewind");
        }
        try(VerifiedArchiveInput checked=new VerifiedArchiveInput(new ByteArrayInputStream(bytes),bytes.length-1,()->{})) {checked.finish();throw new AssertionError();}catch(IOException expected){checks++;}
        int[] ticks={0};try(VerifiedArchiveInput checked=new VerifiedArchiveInput(new ByteArrayInputStream(new byte[200000]),300000,()->{if(++ticks[0]>=2)throw new java.util.concurrent.CancellationException();})) {checked.finish();throw new AssertionError();}catch(java.util.concurrent.CancellationException expected){checks++;}
    }
    private static void names() {
        check(LookNameRules.unique(" Look ",List.of()).equals("Look"),"trim");
        check(LookNameRules.unique("LOOK",List.of("Look","LOOK (2)")).equals("LOOK (3)"),"case-insensitive disambiguation");
        String longName="Образ".repeat(20);String unique=LookNameRules.unique(longName,List.of(longName.substring(0,80)));
        check(unique.length()<=80 && unique.endsWith(" (2)"),"bounded Ukrainian name");
        String emoji=LookNameRules.unique("a".repeat(79)+"👗",List.of());
        check(emoji.length()==79 && !Character.isHighSurrogate(emoji.charAt(emoji.length()-1)),"name never truncates half an emoji");
        String duplicateEmoji=LookNameRules.unique("a".repeat(75)+"👗"+"abc",List.of("a".repeat(75)+"👗"+"abc"));
        check(duplicateEmoji.equals("a".repeat(75)+" (2)"),"suffix truncation preserves complete code points");
        List<String> existing=new ArrayList<>();for(int i=0;i<50;i++){String name=LookNameRules.unique("Zestaw na dziś",existing);check(!existing.contains(name),"50 distinguishable outfit names");existing.add(name);}
    }
    private static void review() {
        List<String> order=List.of("one","failed","two","working","three","four","foreign","five");
        Set<String> ready=new HashSet<>(List.of("one","two","three","four","five"));
        String current="one";List<String> visited=new ArrayList<>();
        while(current!=null) {visited.add(current);ready.remove(current);current=ImportReviewRules.next(order,ready,Set.of(),current);}
        check(visited.equals(List.of("one","two","three","four","five")),"five reviews skip failed, working and foreign entries");
        check(ImportReviewRules.next(order,Set.of("one","two","three"),Set.of("two"),"one").equals("three"),"deferred remains in queue but is skipped in session");
        check(ImportReviewRules.next(order,Set.of("one"),Set.of(),"one")==null,"saved row cannot be chosen again from stale observation");
        check(ImportReviewRules.next(order,Set.of("foreign"),Set.of("foreign"),"")==null,"scope and deferred set are respected");
    }
    public static void main(String[] args) throws Exception {diagnostics();geometry();archive();names();review();System.out.println("PASS: "+checks+" production empty-reason, zoom/crop, mask cancellation, verified-archive, outfit-name and sequential-review assertions.");}
}
