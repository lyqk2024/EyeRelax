package com.eye.tutorial;

import com.eye.i18n.I18n;
import com.eye.ui.theme.ProportionalPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.function.*;

/** Optional guides over live controls. Navigation never writes application settings. */
public final class TutorialController implements AutoCloseable {
    public enum Interaction { DEFAULT, RESUME, ADVANCE_ON_POPUP_SELECTION, FINISH_ON_POPUP_OPEN }
    public record Step(String message,Supplier<JComponent> target,Runnable prepare,BooleanSupplier done,Interaction interaction){
        public Step(String message,Supplier<JComponent> target,Runnable prepare,BooleanSupplier done){this(message,target,prepare,done,Interaction.DEFAULT);}
    }
    public record FeatureTour(String id,String introducedVersion,String prompt,List<Step> steps){
        public FeatureTour{steps=List.copyOf(steps);if(steps.isEmpty())throw new IllegalArgumentException("A course needs a step");}
    }
    private final Window window;
    private final RootPaneContainer rootHost;
    private final JRootPane root;
    private final TutorialState state;
    private final BooleanSupplier blocked;
    private final List<Step> welcome;
    private final List<FeatureTour> features=new ArrayList<>();
    private final Map<String,FeatureTour> topics=new LinkedHashMap<>();
    private final Set<String> sessionOffers=new HashSet<>();
    private List<Step> steps;
    private FeatureTour feature;
    private String topicId;
    private final TutorialOverlay overlay=new TutorialOverlay();
    private final Component oldGlass;
    private final javax.swing.Timer timer,animation;
    private final TutorialPopupGate popups;
    private final KeyEventDispatcher keys=this::key;
    private final WindowAdapter lifecycle=new WindowAdapter(){@Override public void windowClosed(WindowEvent e){close();}};
    private boolean active,waitingResume,initialDone,offered,launchRequested,manifestRegistered,manualReplay,closed;
    private boolean modalOpened,settingsVisit;
    private int step,settingsStep=-1,renderedPosition=-1,renderedCount=-1;
    private Runnable openSettings;
    private Supplier<JComponent> settingsTarget;
    private JComponent boundTarget,boundRegion,scrolledTarget;

    public TutorialController(Window window,TutorialState state,List<Step> welcome,BooleanSupplier blocked){
        this.window=window;this.state=state;this.welcome=List.copyOf(welcome);this.blocked=blocked;
        rootHost=(RootPaneContainer)window;root=rootHost.getRootPane();oldGlass=rootHost.getGlassPane();rootHost.setGlassPane(overlay);overlay.setVisible(false);
        timer=new javax.swing.Timer(80,e->tick());
        animation=new javax.swing.Timer(16,e->{overlay.animate();if(!overlay.isAnimating())((javax.swing.Timer)e.getSource()).stop();});
        popups=new TutorialPopupGate(this::popupOpened,this::popupClosed);
        overlay.skip.addActionListener(e->{if(settingsVisit&&!waitingResume)returnFromSettings();else finish(!(active&&!waitingResume&&nextApplicable(step,1)<0));});
        overlay.previous.addActionListener(e->{if(active&&!waitingResume&&!settingsVisit){int previous=nextApplicable(step,-1);if(previous>=0)show(previous);}});
        overlay.next.addActionListener(e->{
            if(waitingResume){waitingResume=false;active=true;if(settingsVisit)showSettingsVisit();else show(step);}
            else if(settingsVisit)returnFromSettings();
            else if(!active&&feature!=null)startFeature(feature);
            else advance();
        });
        overlay.returnToTutorial.addActionListener(e->returnFromSettings());
        overlay.settings.addActionListener(e->visitSettings());
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        window.addWindowListener(lifecycle);
    }
    public boolean automaticWelcomePending(){return state.shouldOfferWelcome();}
    /** Saving stays with the settings page's native Save button. */
    public void configureSettingsShortcut(int index,Runnable open,Supplier<JComponent> target,BooleanSupplier ignoredSave){settingsStep=index;openSettings=open;settingsTarget=target;}
    public boolean openModeSettings(){if(!active||step!=settingsStep||feature!=null)return false;visitSettings();return true;}
    private void visitSettings(){
        if(!active||step!=settingsStep||feature!=null||settingsVisit||openSettings==null)return;
        settingsVisit=true;popups.close();root.putClientProperty("tutorial.formHelp",null);root.putClientProperty("tutorial.blocked",null);openSettings.run();showSettingsVisit();
    }
    private void showSettingsVisit(){overlay.showSettingsReturn(settingsTarget);animation.stop();}
    private void returnFromSettings(){
        if(!settingsVisit)return;settingsVisit=false;Step current=steps.get(step);
        if(current.done()!=null&&!initialDone&&current.done().getAsBoolean())advance();else show(step);
    }
    public void registerTopic(String id,String title,List<Step> steps){topics.put(id,new FeatureTour(id,"",title,steps));}
    public Set<String> topicIds(){return Set.copyOf(topics.keySet());}
    public void registerFeatureTour(FeatureTour tour){features.add(tour);}
    private void registerManifest(){
        if(manifestRegistered)return;manifestRegistered=true;
        Map<String,String> versions=new LinkedHashMap<>();for(FeatureTour tour:features)versions.put(tour.id(),tour.introducedVersion());
        state.registerFeatures(versions);
    }
    public void dismissTopicOffer(){
        if(topicId!=null&&!active&&!waitingResume){feature=null;topicId=null;overlay.setVisible(false);timer.stop();}
    }
    public void offerTopic(String id){
        if(closed||!window.isDisplayable()||active||waitingResume||settingsVisit||state.shouldOfferWelcome()
                ||blocked.getAsBoolean()||hasModal(window)||sessionOffers.contains(id))return;
        FeatureTour tour=topics.get(id);if(tour==null||!state.shouldOfferTopic(id))return;
        // An upgrade prompt has priority; page navigation must not replace it.
        if(feature!=null&&topicId==null)return;
        dismissTopicOffer();sessionOffers.add(id);manualReplay=false;
        topicId=id;feature=tour;steps=tour.steps();step=Math.min(state.topicStep(id),steps.size()-1);showOffer();timer.start();
    }
    public void startTopic(String id){
        FeatureTour tour=topics.get(id);if(tour==null||closed)return;
        resetPresentation();manualReplay=!state.shouldOfferTopic(id);topicId=id;feature=tour;
        if(!manualReplay)state.progressTopic(id,0);
        startFeature(tour);
    }
    public void offerOnLaunch(){
        if(closed||offered)return;registerManifest();launchRequested=true;
        if(active||waitingResume)return;
        if(!window.isVisible()||blocked.getAsBoolean()||hasModal(window)){timer.start();return;}
        launchRequested=false;offered=true;manualReplay=false;
        if(state.shouldOfferWelcome()){
            topicId=null;feature=null;steps=welcome;step=Math.min(state.step(),welcome.size()-1);
            if(state.interrupted()){waitingResume=true;overlay.showContinue(I18n.text("继续上次的使用教程？也可以跳过。"));timer.start();}
            else startWelcome(false);
        }else for(FeatureTour candidate:features)if(state.shouldOfferFeature(candidate.id(),candidate.introducedVersion())){
            topicId=null;feature=candidate;steps=candidate.steps();step=Math.min(state.featureStep(candidate.id()),steps.size()-1);showOffer();timer.start();break;
        }
    }
    private void showOffer(){
        overlay.showContinue(I18n.text(feature.prompt()));
        overlay.next.setText(I18n.text(topicId==null&&state.featureInterrupted(feature.id())?"继续教程":topicId==null?"看看":"了解一下"));
        overlay.skip.setText(I18n.text("暂时跳过"));
    }
    public void startWelcome(boolean manual){
        if(closed||welcome.isEmpty())return;
        resetPresentation();topicId=null;feature=null;steps=welcome;manualReplay=manual&&!state.shouldOfferWelcome();
        active=true;step=manual?0:Math.min(state.step(),welcome.size()-1);
        if(!manualReplay)state.begin();show(step);timer.start();
    }
    private void startFeature(FeatureTour tour){
        feature=tour;steps=tour.steps();waitingResume=false;active=true;
        show(manualReplay?0:Math.min(topicId==null?state.featureStep(tour.id()):state.topicStep(topicId),steps.size()-1));timer.start();
    }
    private void resetPresentation(){
        active=waitingResume=settingsVisit=modalOpened=false;launchRequested=false;
        feature=null;topicId=null;boundTarget=boundRegion=scrolledTarget=null;popups.close();animation.stop();overlay.setVisible(false);
        root.putClientProperty("tutorial.formHelp",null);root.putClientProperty("tutorial.blocked",null);
    }
    private void show(int index){
        settingsVisit=false;modalOpened=false;step=index;popups.close();boundTarget=boundRegion=scrolledTarget=null;
        // A form can hide a section after the person chooses a different rule type.
        // Omit only those now-inapplicable local hints, never navigate by changing values.
        if(isFormGuide()&&window.isShowing()){
            while(step<steps.size()){
                Step candidate=steps.get(step);candidate.prepare().run();JComponent target=candidate.target().get();
                if(target!=null&&target.isShowing())break;step++;
            }
            if(step>=steps.size()){finish(false);return;}
        }
        root.putClientProperty("tutorial.formHelp",true);root.putClientProperty("tutorial.blocked",blocked);
        if(!manualReplay){if(topicId!=null)state.progressTopic(topicId,step);else if(feature==null)state.progress(step);else state.progressFeature(feature.id(),step);}
        Step current=steps.get(step);current.prepare().run();initialDone=current.done()!=null&&current.done().getAsBoolean();
        renderStep(true);
    }
    private void renderStep(boolean animate){
        if(closed||!active)return;
        renderedPosition=displayPosition();renderedCount=displayCount();
        Step current=steps.get(step);overlay.showStep(I18n.text(current.message()),renderedPosition,renderedCount,current.done()!=null&&!initialDone);
        overlay.settings.setVisible(feature==null&&step==settingsStep);overlay.settings.setText(I18n.text("前往设置"));
        updateTarget(animate);if(animate)animation.start();
    }
    private void popupOpened(JComponent control){
        if(!active||settingsVisit||closed)return;
        Step current=steps.get(step);
        if(current.interaction()==Interaction.FINISH_ON_POPUP_OPEN&&control==current.target().get()){
            finish(false);return;
        }
        overlay.setVisible(false);animation.stop();
    }
    private void popupClosed(JComponent control,boolean changed){
        if(closed||!active||settingsVisit)return;
        if(changed&&steps.get(step).interaction()==Interaction.ADVANCE_ON_POPUP_SELECTION){advance();return;}
        tick();
        if(active&&!popups.isOpen()&&!hasModal(window)&&!blocked.getAsBoolean()&&control.isShowing())control.requestFocusInWindow();
    }
    private boolean applicable(int index){
        if(!isFormGuide()||!window.isShowing())return true;
        JComponent target=steps.get(index).target().get();return target!=null&&target.isShowing();
    }
    private int nextApplicable(int from,int direction){
        for(int index=from+direction;index>=0&&index<steps.size();index+=direction)if(applicable(index))return index;
        return -1;
    }
    private int displayPosition(){int count=0;for(int i=0;i<step;i++)if(applicable(i))count++;return count;}
    private int displayCount(){int count=0;for(int i=0;i<steps.size();i++)if(applicable(i))count++;return Math.max(1,count);}
    private void advance(){if(!active)return;int next=nextApplicable(step,1);if(next<0)finish(false);else show(next);}
    public void tick(){
        if(closed)return;
        if(launchRequested&&!active&&!waitingResume){offerOnLaunch();if(launchRequested)return;}
        if(!window.isVisible()){overlay.setVisible(false);animation.stop();return;}
        if(blocked.getAsBoolean()){
            if(active){active=false;waitingResume=true;}overlay.setVisible(false);animation.stop();return;
        }
        if(hasModal(window)){
            if(active&&(feature!=null||isFormGuide())&&!settingsVisit)modalOpened=true;
            overlay.setVisible(false);animation.stop();return;
        }
        if(active&&modalOpened){modalOpened=false;if(steps.get(step).interaction()!=Interaction.RESUME){advance();return;}}
        if(popups.isOpen()){overlay.setVisible(false);animation.stop();return;}
        if(waitingResume){if(!overlay.isVisible())overlay.showContinue(I18n.text("可以继续教程了。"));return;}
        if(!active){if(feature!=null&&!overlay.isVisible())showOffer();return;}
        if(settingsVisit){
            if(!overlay.isVisible())showSettingsVisit();JComponent save=settingsTarget.get();if(save!=null)overlay.setScale(ProportionalPanel.scaleOf(save));
            overlay.revalidate();overlay.repaint();return;
        }
        Step current=steps.get(step);
        if(current.done()!=null&&!initialDone&&current.done().getAsBoolean()){advance();return;}
        if(!applicable(step)){advance();return;}
        if(!overlay.isVisible()||renderedPosition!=displayPosition()||renderedCount!=displayCount())renderStep(false);else updateTarget(false);
    }
    private static boolean hasModal(Window owner){
        for(Window child:owner.getOwnedWindows())if(child.isVisible()&&(child instanceof Dialog d&&d.isModal()||hasModal(child)))return true;
        return false;
    }
    private boolean isFormGuide(){return root.getClientProperty("tutorial.formGuide")==this;}
    private void updateTarget(boolean animate){
        if(overlay.getWidth()!=root.getWidth()||overlay.getHeight()!=root.getHeight())overlay.setBounds(0,0,root.getWidth(),root.getHeight());
        JComponent raw=steps.get(step).target().get(),target=TutorialTargets.region(raw);
        if(raw!=boundTarget||target!=boundRegion){boundTarget=raw;boundRegion=target;popups.bind(target);}
        if(target==null||!target.isShowing()){overlay.focus(null,false);return;}
        overlay.setScale(ProportionalPanel.scaleOf(target));
        if(target!=scrolledTarget||animate){target.scrollRectToVisible(new Rectangle(0,0,target.getWidth(),target.getHeight()));scrolledTarget=target;}
        Rectangle bounds=SwingUtilities.convertRectangle(target,new Rectangle(0,0,target.getWidth(),target.getHeight()),overlay);
        if(target.getClientProperty("tutorial.label") instanceof JComponent label&&label.isShowing())bounds=bounds.union(SwingUtilities.convertRectangle(label,new Rectangle(0,0,label.getWidth(),label.getHeight()),overlay));
        bounds.grow(5,5);bounds=bounds.intersection(new Rectangle(0,0,overlay.getWidth(),overlay.getHeight()));overlay.focus(bounds,animate);
    }
    private boolean key(KeyEvent e){
        if(closed||!overlay.isVisible()||SwingUtilities.getWindowAncestor(e.getComponent())!=window)return false;
        if((!settingsVisit||waitingResume)&&e.getKeyCode()==KeyEvent.VK_ESCAPE){if(e.getID()==KeyEvent.KEY_PRESSED)finish(true);return true;}
        if(!active)return false;
        if(e.isAltDown()&&e.getKeyCode()==KeyEvent.VK_S&&step==settingsStep&&feature==null){if(e.getID()==KeyEvent.KEY_PRESSED)visitSettings();return true;}
        if(e.isAltDown()&&e.getKeyCode()==KeyEvent.VK_R&&settingsVisit){if(e.getID()==KeyEvent.KEY_PRESSED)returnFromSettings();return true;}
        if(settingsVisit)return false;
        JComponent target=TutorialTargets.region(steps.get(step).target().get());Component source=e.getComponent();
        return !SwingUtilities.isDescendingFrom(source,overlay)&&source!=target&&(target==null||!SwingUtilities.isDescendingFrom(source,target));
    }
    public void finish(boolean skipped){
        if(closed)return;
        if(!manualReplay){
            if(topicId!=null)state.finishTopic(topicId,skipped);
            else if(feature==null){if(active||waitingResume)state.finish(skipped);}
            else state.finishFeature(feature.id(),skipped);
        }
        resetPresentation();timer.stop();
    }
    public boolean isActive(){return active;}
    public int currentStep(){return step;}
    public TutorialOverlay overlay(){return overlay;}
    @Override public void close(){
        if(closed)return;closed=true;timer.stop();animation.stop();popups.close();overlay.setVisible(false);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);window.removeWindowListener(lifecycle);
        if(rootHost.getGlassPane()==overlay){root.putClientProperty("tutorial.formHelp",null);root.putClientProperty("tutorial.blocked",null);rootHost.setGlassPane(oldGlass);}
        if(root.getClientProperty("tutorial.formGuide")==this)root.putClientProperty("tutorial.formGuide",null);
    }
}
