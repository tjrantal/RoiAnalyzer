package timo.deakin.ellipsefit;

//Optimisation
import Jama.Matrix;
import timo.deakin.ellipsefit.nonlinlsqoptim.EllipseOptimAlgorithm;

public class NormalPoint implements Comparable<NormalPoint>{
	public double x;
	public double y;
	public double theta;
	public double thetaMapped;
	public double distance;
	public NormalPoint(double x, double y, double theta, double thetaMapped, double distance){
		this.x = x;
		this.y= y;
		this.theta = theta;
		this.thetaMapped = thetaMapped;
		this.distance = distance;
	}
	

	/*
		Implement Comparable
		For Ascending order
	*/
	@Override
    public int compareTo(NormalPoint a) {
		return this.distance <= a.distance ?  -1: 1; //which is larger
    }
	
}