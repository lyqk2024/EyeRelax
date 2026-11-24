package com.eye.tutorial;

import com.eye.ui.theme.Theme;
import com.eye.i18n.I18n;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Spotlight presentation only. Only the final rounded target is interactive during a lesson. */
public final class TutorialOverlay extends JPanel {
 private Rectangle target=new Rectangle();
 private Rectangle2D.Double hole=new Rectangle2D.Double();
 private long animationStart;
 private boolean animating,passThrough,promptOnly;
 private double scale=1;
 public final JButton returnToTutorial=new JButton(I18n.text("返回教程 · Alt+R"));
 private boolean settingsReturn;
 private java.util.function.Supplier<JComponent> saveTarget;
 private Rectangle saveBounds=new Rectangle();
 private final JPanel saveBubble=Theme.card(new BorderLayout());
 private final JLabel saveText=Theme.label(I18n.text("点击保存设置，保存修改。"),12,Theme.INK);
 private final JPanel card=Theme.card(new BorderLayout(0,12));
 private final JLabel text=Theme.label("",13,Theme.INK),progress=Theme.label("",11,Theme.SECONDARY);
 public final JButton previous=new JButton(I18n.text("上一步")),next=new JButton(I18n.text("下一步")),skip=new JButton(I18n.text("跳过教程"));
 public final JButton settings=new JButton(I18n.text("前往设置"));
 private final JLabel settingsHint=Theme.label(I18n.text("也可在设置里调整，点击："),11,Theme.SECONDARY);
 private final FlowRow settingsRow=new FlowRow(false,5);
 private final FlowRow actions=new FlowRow(true,7);
 private final JPanel header=Theme.panel(new BorderLayout(8,0));
 private final JPanel footer=Theme.panel(new BorderLayout(0,8));

 public TutorialOverlay(){
  setOpaque(false);setLayout(null);setFocusable(true);setFocusCycleRoot(true);setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
  Theme.smallButton(returnToTutorial,"secondary",null);returnToTutorial.setVisible(false);add(returnToTutorial);
  saveBubble.setBorder(new EmptyBorder(10,14,10,14));saveBubble.add(saveText);saveBubble.setVisible(false);add(saveBubble);
  card.setBorder(new EmptyBorder(16,18,16,18));text.setVerticalAlignment(SwingConstants.TOP);
  header.add(progress);Theme.smallButton(skip,"ghost",null);header.add(skip,BorderLayout.EAST);card.add(header,BorderLayout.NORTH);card.add(text);
  Theme.smallButton(settings,"secondary",null);settings.setVisible(false);settings.setToolTipText("Alt+S");
  Theme.smallButton(previous,"secondary",null);Theme.smallButton(next,"primary",null);actions.add(previous);actions.add(next);
  settingsRow.add(settingsHint);settingsRow.add(settings);footer.add(settingsRow,BorderLayout.NORTH);footer.add(actions,BorderLayout.SOUTH);card.add(footer,BorderLayout.SOUTH);add(card);
 }
 public void showStep(String message,int step,int count,boolean required){
  settingsReturn=false;returnToTutorial.setVisible(false);saveBubble.setVisible(false);card.setVisible(true);promptOnly=false;
  text.setText("<html>"+Theme.escape(message)+"</html>");progress.setText((step+1)+" / "+count);
  previous.setVisible(step>0);previous.setEnabled(step>0);skip.setVisible(step<count-1);skip.setText(I18n.text("跳过教程"));
  next.setVisible(true);next.setText(I18n.text(step==count-1?"结束教程":"下一步"));next.putClientProperty("rest.variant",step==count-1?"danger":"primary");next.setForeground(step==count-1?Theme.DANGER:Color.WHITE);
  setVisible(true);revalidate();repaint();requestFocusInWindow();
 }
 public void showContinue(String message){
  settingsReturn=false;returnToTutorial.setVisible(false);saveBubble.setVisible(false);card.setVisible(true);promptOnly=true;settings.setVisible(false);
  text.setText("<html>"+Theme.escape(message)+"</html>");progress.setText(I18n.text("使用教程"));previous.setVisible(false);previous.setEnabled(false);skip.setVisible(true);skip.setText(I18n.text("跳过教程"));
  next.putClientProperty("rest.variant","primary");next.setForeground(Color.WHITE);next.setVisible(true);next.setText(I18n.text("继续教程"));
  passThrough=false;animating=false;hole=new Rectangle2D.Double();setVisible(true);revalidate();repaint();
 }
 public void showSettingsReturn(java.util.function.Supplier<JComponent> save){
  settingsReturn=true;promptOnly=true;saveTarget=save;card.setVisible(true);returnToTutorial.setVisible(false);
  progress.setText(I18n.text("使用教程"));text.setText("<html>"+Theme.escape(I18n.text("可在设置里自由调整。完成后返回教程。"))+"</html>");
  previous.setVisible(false);skip.setVisible(false);settings.setVisible(false);settingsRow.setVisible(false);next.setVisible(true);next.setText(I18n.text("返回教程 · Alt+R"));next.putClientProperty("rest.variant","primary");next.setForeground(Color.WHITE);
  saveBubble.setVisible(true);setVisible(true);revalidate();repaint();
 }
 public void focus(Rectangle bounds,boolean animate){
  Rectangle clipped=bounds==null?new Rectangle():bounds.intersection(new Rectangle(0,0,getWidth(),getHeight()));
  if(clipped.isEmpty()){target=new Rectangle();passThrough=false;animating=false;hole=new Rectangle2D.Double();revalidate();repaint();return;}
  boolean changed=!target.equals(clipped);if(!changed&&!animate&&passThrough)return;target=clipped;passThrough=true;
  if(animate){animationStart=System.nanoTime();animating=true;hole=new Rectangle2D.Double(0,0,getWidth(),getHeight());}
  else if(!animating&&changed)hole=new Rectangle2D.Double(target.x,target.y,target.width,target.height);
  revalidate();repaint();
 }
 public void animate(){
  if(animating){double t=Math.min(1,(System.nanoTime()-animationStart)/350_000_000.0),ease=1-Math.pow(1-t,3);hole=new Rectangle2D.Double(target.x*ease,target.y*ease,getWidth()+(target.width-getWidth())*ease,getHeight()+(target.height-getHeight())*ease);if(t>=1)animating=false;repaint();}
 }
 public boolean isAnimating(){return animating;}
 public void setScale(double value){
  if(Math.abs(scale-value)<0.01)return;scale=value;
  returnToTutorial.setFont(Theme.font((float)(12*scale)));saveText.setFont(Theme.font((float)(12*scale)));settingsHint.setFont(Theme.font((float)(11*scale)));text.setFont(Theme.font((float)(13*scale)));progress.setFont(Theme.font((float)(11*scale)));
  for(JButton b:new JButton[]{previous,next,skip,settings}){b.setFont(Theme.font((float)(12*scale)));b.setBorder(new EmptyBorder(px(7),px(12),px(7),px(12)));}
  card.setBorder(new EmptyBorder(px(16),px(18),px(16),px(18)));saveBubble.setBorder(new EmptyBorder(px(10),px(14),px(10),px(14)));
  ((BorderLayout)card.getLayout()).setVgap(px(12));((BorderLayout)footer.getLayout()).setVgap(px(8));settingsRow.setScale(scale);actions.setScale(scale);revalidate();
 }
 private int px(double value){return (int)Math.round(value*scale);}
 private Shape holeShape(){return new RoundRectangle2D.Double(hole.x,hole.y,hole.width,hole.height,px(14),px(14));}
 @Override public boolean contains(int x,int y){
  if(!isVisible())return false;
  if(card.isVisible()&&card.getBounds().contains(x,y))return true;
  return !promptOnly&&!(passThrough&&!animating&&holeShape().contains(x,y));
 }
 private int measureCard(int width){
  Insets in=card.getInsets();int inner=Math.max(1,width-in.left-in.right);
  settingsRow.setVisible(!settingsReturn&&settings.isVisible());settingsHint.setVisible(!promptOnly);
  settingsRow.widthForLayout(inner);actions.widthForLayout(inner);
  Object html=text.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);int textHeight=text.getPreferredSize().height;
  if(html instanceof javax.swing.text.View view){view.setSize(inner,0);textHeight=(int)Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS))+2;}
  int headerHeight=header.getPreferredSize().height;
  int footerHeight=footer.getPreferredSize().height;
  return in.top+in.bottom+headerHeight+textHeight+footerHeight+px(24);
 }
 private Rectangle fit(int x,int y,int width,int height,int margin){
  return new Rectangle(Math.max(margin,Math.min(getWidth()-margin-width,x)),Math.max(margin,Math.min(getHeight()-margin-height,y)),width,height);
 }
 private Rectangle placeCard(int width,int height,int margin){
  if(promptOnly||target.isEmpty())return fit(getWidth()-width-margin,margin,width,height,margin);
  int gap=px(12),alignedX=Math.max(margin,Math.min(getWidth()-width-margin,target.x)),alignedY=Math.max(margin,Math.min(getHeight()-height-margin,target.y));
  List<Rectangle> choices=List.of(
    new Rectangle(alignedX,target.y+target.height+gap,width,height),
    new Rectangle(alignedX,target.y-height-gap,width,height),
    new Rectangle(target.x+target.width+gap,alignedY,width,height),
    new Rectangle(target.x-width-gap,alignedY,width,height));
  Rectangle usable=new Rectangle(margin,margin,Math.max(1,getWidth()-margin*2),Math.max(1,getHeight()-margin*2));
  for(Rectangle choice:choices)if(usable.contains(choice)&&!choice.intersects(target))return choice;
  // Prefer a narrower, correctly wrapped card beside a tall target before accepting overlap.
  for(boolean right:new boolean[]{true,false}){
   int available=right?getWidth()-margin-target.x-target.width-gap:target.x-margin-gap;
   if(available<px(250)||available>=width)continue;
   int candidateHeight=measureCard(available);
   if(candidateHeight<=usable.height){int x=right?target.x+target.width+gap:margin;Rectangle candidate=fit(x,target.y,available,candidateHeight,margin);if(!candidate.intersects(target))return candidate;}
  }
  Rectangle best=null;long leastOverlap=Long.MAX_VALUE;
  for(Rectangle choice:choices){Rectangle candidate=fit(choice.x,choice.y,width,height,margin);Rectangle overlap=candidate.intersection(target);long area=overlap.isEmpty()?0:(long)overlap.width*overlap.height;if(area<leastOverlap){leastOverlap=area;best=candidate;}}
  return best;
 }
 @Override public void doLayout(){
  int margin=Math.min(px(12),Math.max(0,(getWidth()-1)/2));
  int width=Math.max(1,Math.min(px(350),getWidth()-2*margin)),height=measureCard(width);
  Rectangle placement=placeCard(width,height,margin);card.setBounds(placement);measureCard(placement.width);card.doLayout();
  if(settingsReturn)layoutSaveBubble(margin);else{saveBubble.setVisible(false);saveBounds=new Rectangle();}
 }
 private void layoutSaveBubble(int margin){
  JComponent save=saveTarget==null?null:saveTarget.get();if(save==null||!save.isShowing()){saveBubble.setVisible(false);saveBounds=new Rectangle();return;}
  saveBounds=SwingUtilities.convertRectangle(save,new Rectangle(0,0,save.getWidth(),save.getHeight()),this).intersection(new Rectangle(0,0,getWidth(),getHeight()));
  if(saveBounds.isEmpty()){saveBubble.setVisible(false);return;}
  int gap=px(18),available=saveBounds.x-margin-gap;
  // Keep the native save action exposed, even on a narrow viewport.
  int width=Math.min(px(270),Math.max(px(150),available));int height=px(46),x=saveBounds.x-width-gap,y=saveBounds.y+(saveBounds.height-height)/2;
  if(available<px(150)){width=Math.min(px(270),getWidth()-2*margin);x=getWidth()-margin-width;y=saveBounds.y-height-px(14);}
  saveBubble.setBounds(fit(x,y,width,height,margin));saveBubble.setVisible(true);
 }
 @Override protected void paintComponent(Graphics graphics){
  Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
  if(settingsReturn){
   if(!saveBounds.isEmpty()){
    g.setColor(Theme.GREEN);g.setStroke(new BasicStroke((float)(1.5*scale),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10,new float[]{(float)(5*scale),(float)(4*scale)},0));g.drawRoundRect(saveBounds.x-5,saveBounds.y-5,saveBounds.width+10,saveBounds.height+10,px(12),px(12));
    Rectangle bubble=saveBubble.getBounds();if(bubble.x+bubble.width<saveBounds.x){int y=bubble.y+bubble.height/2;g.drawLine(bubble.x+bubble.width,y,saveBounds.x-6,y);}else{int x=Math.max(saveBounds.x+8,Math.min(saveBounds.x+saveBounds.width-8,bubble.x+bubble.width/2));g.drawLine(x,bubble.y+bubble.height,x,saveBounds.y-6);}
   }
  }else if(!promptOnly){
   Area shade=new Area(new Rectangle(0,0,getWidth(),getHeight()));if(passThrough)shade.subtract(new Area(holeShape()));g.setColor(new Color(0,0,0,160));g.fill(shade);
   if(passThrough){g.setColor(Theme.SAGE);g.setStroke(new BasicStroke(2));g.draw(holeShape());}
  }
  g.dispose();
 }
 /** FlowLayout's preferred height does not include wrapped rows; this one does. */
 private static final class FlowRow extends JPanel {
  private final boolean right;private final int baseGap;private int width=300,gap,rowGap=6;
  FlowRow(boolean right,int gap){this.right=right;this.baseGap=gap;this.gap=gap;setOpaque(false);setLayout(null);}
  void setScale(double scale){gap=(int)Math.round(baseGap*scale);rowGap=(int)Math.round(6*scale);}
  void widthForLayout(int width){this.width=Math.max(1,width);}
  private List<List<Component>> rows(){List<List<Component>> result=new ArrayList<>();List<Component> row=new ArrayList<>();int used=0;for(Component c:getComponents())if(c.isVisible()){int w=Math.min(width,c.getPreferredSize().width);if(!row.isEmpty()&&used+gap+w>width){result.add(row);row=new ArrayList<>();used=0;}if(!row.isEmpty())used+=gap;row.add(c);used+=w;}if(!row.isEmpty())result.add(row);return result;}
  @Override public Dimension getPreferredSize(){int height=0;for(List<Component> row:rows()){if(height>0)height+=rowGap;height+=row.stream().mapToInt(c->c.getPreferredSize().height).max().orElse(0);}return new Dimension(width,height);}
  @Override public void doLayout(){widthForLayout(getWidth());int y=0;for(List<Component> row:rows()){int rowWidth=row.stream().mapToInt(c->Math.min(width,c.getPreferredSize().width)).sum()+Math.max(0,row.size()-1)*gap;int height=row.stream().mapToInt(c->c.getPreferredSize().height).max().orElse(0),x=right?Math.max(0,width-rowWidth):0;for(Component c:row){Dimension d=c.getPreferredSize();int w=Math.min(width,d.width);c.setBounds(x,y+(height-d.height)/2,w,d.height);x+=w+gap;}y+=height+rowGap;}}
 }
}
