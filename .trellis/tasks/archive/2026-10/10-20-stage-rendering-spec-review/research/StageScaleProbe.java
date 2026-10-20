import com.m0e_n00b.viriviri.*;
public class StageScaleProbe {
 public static void main(String[] args) {
  PlaybackStageController<Object> c = new PlaybackStageController<>(1.6f,.9f,k->new Object(),(l,p)->true);
  for(float s: new float[]{1f,1.5f,2f}) {
   c.setParameters(new PlaybackStageParameters(s,null,new PlaybackStageCurvature.Cylinder(2.7f)));
   PlaybackStageGeometry g=c.getLayout().getGeometry();
   System.out.printf("s=%.1f W=%.3f H=%.3f radius=%.3f desiredRadius=%.3f%n",s,g.getViewport().getWidthMeters(),g.getViewport().getHeightMeters(),g.getCurvature().radiusOrNull(),2.7f*s);
   for(PlaybackStageLayerGeometry l:c.getLayout().getLayers()) {
    float z=l.localPose().getT().getZ();
    System.out.printf("  %s localZ=%.4f desiredZ=%.4f centralZ=%.4f%n",l.getRole(),z,(l.getRole().getBaseLocalZ()-2.7f)*s,z+g.getCurvature().radiusOrNull());
   }
  }
  System.out.println("curvature-only change at s=2:");
  for(float r: new float[]{2.7f,1.5f}) {
   c.setParameters(new PlaybackStageParameters(2f,null,new PlaybackStageCurvature.Cylinder(r)));
   PlaybackStageLayerGeometry l=c.getLayout().getLayers().get(0);
   System.out.printf("  radius=%.3f localZ=%.3f desiredZ=%.3f%n",r,l.localPose().getT().getZ(),-r*2f);
  }
 }
}