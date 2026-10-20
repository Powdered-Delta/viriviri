import com.meta.spatial.core.*;
public class CoordinateProbe {
  public static void main(String[] args) {
    System.out.println("identity.forward=" + new Pose().forward());
    System.out.println("identity.right=" + new Pose().right());
    System.out.println("Vector3.Forward=" + Vector3.Companion.getForward());
    System.out.println("Vector3.Right=" + Vector3.Companion.getRight());
    System.out.println("Vector3.Up=" + Vector3.Companion.getUp());
    System.out.println("yaw+90.forward=" + new Quaternion(0f,90f,0f).times(new Vector3(0f,0f,1f)));
  }
}