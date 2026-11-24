package com.eye.stats;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
public final class HourlyPaletteAcceptance {
 public static void main(String[] args)throws Exception{
  var f=HourlyFocusChart.class.getDeclaredField("FALLBACK");f.setAccessible(true);Color[] palette=(Color[])f.get(null);
  int[] old={0x13B975,0x075FF4,0x8D24EC,0xFF8400,0xE71D79,0x09BDA0,0x09A9CF,0x7D8F0C,0x3F3ED1,0xEF5B48};
  if(palette.length<20||new HashSet<>(Arrays.asList(palette)).size()!=palette.length)throw new AssertionError("20 distinct swatches");
  for(int i=0;i<old.length;i++)if((palette[i].getRGB()&0xffffff)!=old[i])throw new AssertionError("legacy swatch changed");
  Map<String,Long> values=new LinkedHashMap<>();Map<String,String> labels=new LinkedHashMap<>();List<Statistics.Bucket> buckets=new ArrayList<>();
  for(int i=0;i<20;i++){String id="task-"+i;values.put(id,60_000L);labels.put(id,"阅读任务 "+(i+1));buckets.add(new Statistics.Bucket(i+":00",i*3600000L,(i+1)*3600000L,Map.of(id,60_000L),(i+1)+":00"));}
  var assigned=HourlyFocusChart.allocateColors(buckets,labels);if(new HashSet<>(assigned.values()).size()!=20)throw new AssertionError("same-name category tasks must not repeat before 20 colors");
  Collections.reverse(buckets);if(!assigned.equals(HourlyFocusChart.allocateColors(buckets,labels)))throw new AssertionError("order independence");
  if(!HourlyFocusChart.colorFor("research","科研").equals(new Color(0x8D24EC)))throw new AssertionError("legacy category preference");
  com.eye.ui.theme.Theme.install();HourlyFocusChart chart=new HourlyFocusChart();chart.data(buckets,labels,1200000L,false);chart.setSize(1200,480);
  BufferedImage image=new BufferedImage(1200,580,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1200,580);chart.paint(g);
  for(int i=0;i<palette.length;i++){int x=20+(i%10)*118,y=500+(i/10)*38;g.setColor(palette[i]);g.fillRect(x,y,36,24);g.setColor(Color.DARK_GRAY);g.drawString(String.format("%02d #%06X",i+1,palette[i].getRGB()&0xffffff),x+40,y+16);}g.dispose();Path out=Path.of(args[0]);Files.createDirectories(out);ImageIO.write(image,"png",out.resolve("palette-20.png").toFile());
  System.out.println("PASS original ten swatches and semantic preferences, twenty unique task colors, order-independent assignment and rendering");System.exit(0);
 }
}
