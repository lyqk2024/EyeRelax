package com.eye.ui;
import com.eye.ui.theme.CollectionBrowser;
import javax.swing.*;
import java.util.*;
import java.time.LocalDate;
public final class CollectionSelectionAcceptance {
 record Item(String id,String name,boolean enabled){}
 static Object field(Object o,String key)throws Exception{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 public static void main(String[] args)throws Exception{SwingUtilities.invokeAndWait(()->{try{
  var source=new java.util.concurrent.atomic.AtomicReference<java.util.List<Item>>(List.of(new Item("a","Alpha",true),new Item("b","Beta",true)));
  var browser=new CollectionBrowser<Item>(source::get,Item::name,i->"",i->LocalDate.now());browser.identity(Item::id);browser.action("Action",null,Item::enabled,i->{});browser.refresh();
  JList<Item> list=(JList<Item>)field(browser,"list");JButton action=((java.util.List<JButton>)field(browser,"actionButtons")).get(0);list.setSelectedIndex(1);
  for(int n=0;n<5;n++){source.set(List.of(new Item("b","Beta",n!=4),new Item("a","Alpha",true)));browser.refresh();if(list.getSelectedValue()==null||!list.getSelectedValue().id().equals("b"))throw new AssertionError("selection survives refreshed copies and reorder");}
  if(action.isEnabled())throw new AssertionError("predicate reflects refreshed object");source.set(List.of(new Item("a","Alpha",true)));browser.refresh();if(!list.isSelectionEmpty()||action.isEnabled())throw new AssertionError("deleted selection disables action");
  list.setSelectedIndex(0);((JTextField)field(browser,"search")).setText("missing");if(!list.isSelectionEmpty()||action.isEnabled())throw new AssertionError("filtered selection disables action");
 }catch(Exception e){throw new RuntimeException(e);}});System.out.println("PASS repeated refresh, stable identity, reorder, updated action predicates, deletion and search filtering");System.exit(0);}
}
