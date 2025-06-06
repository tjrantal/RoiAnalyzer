package timo.deakin.ellipsefit;

import java.util.ArrayList;
import edu.deakin.timo.utils.Coordinate;
//Optimisation
import Jama.Matrix;
import timo.deakin.ellipsefit.nonlinlsqoptim.EllipseOptimAlgorithm;

public class EllipseFit {
	
	//Known fit from octave a = 24421.36702535019 b = 24386.51325007541 c = 176.1456857297663 d = 973.1253733174899
	private double[][] observedPoints;
	
	private double[] coeffs;
	private double convergenceLimit = 1e-12;	//For optimisation
	private double tolerance = 1e-6;			//For driving normal
	
	public EllipseFit(){
		this(new double[][]{
			{ 50.6024, 212.3494},
			{ 43.8253, 201.5060},
			{34.3373, 188.8554},
			{ 31.1747, 171.6867},
			{37.0482, 154.9699},
			{49.2470, 142.7711},
			{65.5120, 125.6024},
			{74.0964, 122.4398},
			{72.7410, 125.1506},
			{58.7349, 135.9940},
			{50.6024, 146.3855},
			{42.9217, 157.2289},
			{37.0482, 166.2651},
			{35.2410, 178.9157},
			{37.0482, 188.4036},
			{44.7289, 199.2470},
			{52.4096, 207.8313}, 
			{ 51.5060, 214.6084}
		});
	}

	public EllipseFit(ArrayList<Coordinate> a, double convergenceLimit){
		double[][] b = aListToDouble(a);
		this.convergenceLimit = convergenceLimit;
		setPoints(b);	
	}
	
	public EllipseFit(ArrayList<Coordinate> a){
		double[][] b = aListToDouble(a);
		setPoints(b);	
	}
	
	private double[][] aListToDouble(ArrayList<Coordinate> a){
		double[][] b = new double[a.size()][2];
		for (int i = 0; i<a.size();++i){
			b[i][0] = a.get(i).x;
			b[i][1] = a.get(i).y;
		}
		return b;
	}
	
	public EllipseFit(double[][] a){
		setPoints(a);		
	}
	
	public EllipseFit(double[][] a, double convergenceLimit){
		this.convergenceLimit = convergenceLimit;
		setPoints(a);		
	}
	
	public NormalPoint getNormalPoint(Coordinate point){
		return getNormalPoint(new double[]{point.x,point.y});
	}
	
	/**
		Get a point on the ellipse that has a normal that passes through a particular point
		The point on the ellipse is found iteratively by exploring the angle between vectors from the point not on the ellipse to a point on the ellipse
		, and the normal from the point on the ellipse. Initially the point on the ellipse is identified as the intersection of a line from the origin 
		of the ellipse to the circumference of the ellipse.
		
		Depends on apache commmons math for dot product and determinant
		
		@param point the point which we find a point on the ellipse that has a normal that joins the point and the ellipse
		@returns NormalPoint coordinates of the point on the ellipse,corresponding polar coordinate theta, corresponding polar coordinate theta mapped from 0 to 2*pi, and distance from the point to the point on the ellipse
	*/
	public NormalPoint getNormalPoint(double[] point){
		double prevAngle;
		double increment = 1d/180d*Math.PI;
		double theta = Math.atan2(point[1]-coeffs[2],point[0]-coeffs[0]);	//Polar coordinate system theta
		//System.out.println(String.format("Init theta %.2f",theta/Math.PI*180d));
		double[] ellipsePoint = new double[]{coeffs[1]*Math.cos(theta),coeffs[3]*Math.sin(theta)};	//Point on ellipse circumference on the line from ellipse centre to the input point
		double[] centredTarget = new double[]{point[0]-coeffs[0],point[1]-coeffs[2]};
		boolean isIn = inEllipse(point,coeffs);
		double[] targetToEllipse = getTargetToEllipse(ellipsePoint,centredTarget,isIn);
		targetToEllipse = normalise(targetToEllipse); 	//Normalise the vector
		//Get normal on the ellipse point
		double[] ellipseNormal = new double[]{Math.cos(theta)/coeffs[1],Math.sin(theta)/coeffs[3]};
		ellipseNormal = normalise(ellipseNormal); 	//Normalise the vector
		//Calculate the angle, BlockRealMatrix has [i][j] = row, column that is row-major data -> row of data in the 2nd dimension
		
		double currentAngle = Math.atan2(det(targetToEllipse,ellipseNormal),dot(targetToEllipse,ellipseNormal));
		prevAngle = currentAngle;
		//System.out.println(String.format("Current angle %.2f",currentAngle/Math.PI*180d));
		if (currentAngle < 0){
			increment = -increment;
		}
		if (!isIn){
		   increment = -increment; 
		}
		while (Math.abs(currentAngle) > tolerance){
			
			//check if point is in or out of the ellipse. In = ellipse-target, Out = target - ellipse to keep the angle correct
			ellipsePoint = new double[]{coeffs[1]*Math.cos(theta),coeffs[3]*Math.sin(theta)};
			targetToEllipse = getTargetToEllipse(ellipsePoint,centredTarget,isIn);
			targetToEllipse = normalise(targetToEllipse); 	//Normalise the vector
			ellipseNormal = new double[]{Math.cos(theta)/coeffs[1],Math.sin(theta)/coeffs[3]}; //Get direction of normal
			ellipseNormal = normalise(ellipseNormal);	//Normalise to unit vector

			//Use clockwise angle
			//https://stackoverflow.com/questions/14066933/direct-way-of-computing-clockwise-angle-between-2-vectors
			currentAngle = Math.atan2(det(targetToEllipse,ellipseNormal),dot(targetToEllipse,ellipseNormal));
			//Have to keep steering towards the tip of the normal
			if (Math.signum(currentAngle) != Math.signum(prevAngle)){
			   increment = -increment/10; 
			}
			prevAngle = currentAngle;
			theta += increment;
		}
		return new NormalPoint(coeffs[0]+coeffs[1]*Math.cos(theta),coeffs[2]+coeffs[3]*Math.sin(theta),theta,(theta+2*Math.PI) % (2*Math.PI),Math.sqrt(Math.pow(ellipsePoint[0]-centredTarget[0],2d)+Math.pow(ellipsePoint[0]-centredTarget[0],2d)));
	}
	
	
	/**Calculate the dot product of vectors a and b organised into a matrix with a as the first row, and be as the second*/
	private static double det(double[] a, double [] b){
		return a[0]*b[1]-a[1]*b[0];		
	}
	
	/**Calculate the dot product between the vectors*/
	private static double dot(double[] a, double[] b){
		double sum = 0;
		for (int i = 0; i<a.length;++i){
			sum+=a[i]*b[i];
		}
		return sum;
	}
	
	/**
		Normalise the 1D array into a unit vector
	*/
	private static double[] normalise(double[] a){
		double n = getNorm(a);
		double[] b = new double[a.length];
		for (int i = 0; i<a.length;++i){
			b[i] =a[i]/n;
		}
		return b;
	}
	
	/**
		Calculate the 2-norm
		@params a 1D array 
		@returns norm (=magnitude of the vector)
	*/
	private static double getNorm(double[] a){
		double sum = 0;
		for (int i = 0;i<a.length;++i){
			sum+=a[i]*a[i];
		}
		return Math.sqrt(sum);
	}
	
	private static double[] getTargetToEllipse(double[] a,double[] b,boolean isIn){
		return isIn ? new double[]{a[0]-b[0],a[1]-b[1]} : new double[]{b[0]-a[0],b[1]-a[1]};
	}
	
	private static boolean inEllipse(double[] point,double[] coeffs){
		return Math.pow(point[0]-coeffs[0],2d)/Math.pow(coeffs[1],2d)+ Math.pow(point[1]-coeffs[2],2d)/Math.pow(coeffs[3],2d) <= 1 ? true : false;
	}
	  
	public void setPoints(double[][] a){
		observedPoints = new double[a.length][a[0].length];
		//System.out.println("Matrix in");
		for (int i = 0;i<a.length;++i){
			//System.out.print("R "+i);
			for (int j = 0;j<a[i].length;++j){
				observedPoints[i][j] = a[i][j];
				//System.out.print(" "+a[i][j]);
			}
			//System.out.println("");
		}
		//System.out.println("d1 "+observedPoints.length+" d2 "+observedPoints[0].length);
		this.coeffs = calcFit();	
	}
	
	public double[] calcFit(){
		double[] pointsCentre = getMean(observedPoints);
		// least squares problem to solve : modeled radius should be close to target radius
		
		for (int i = 0; i< pointsCentre.length; ++i){
		  //System.out.println(String.format("Centre guess %.1f",pointsCentre[i]));
		}
		
		EllipseOptimAlgorithm eoa = new EllipseOptimAlgorithm(new Matrix(observedPoints),convergenceLimit);
		Matrix optimised = eoa.optimise(new Matrix(new double[] { pointsCentre[0], 1d, pointsCentre[1], 1d},4));
		double[] coeffs = new double[optimised.getRowDimension()];
		
		for (int i = 0; i< optimised.getRowDimension(); ++i){
			coeffs[i] = optimised.get(i,0);
			//System.out.println(String.format("Coeff %.1f",optimised.get(i,0)));
		}
		return coeffs;
	}
	
	public double[] getCoeffs(){
		return coeffs;
	}
	
	/**
		Calculate row means
		@param a matrix for which row means are calculated for
		@returns row means in a 1D array
	*/	
	public static double[] getMean(double[][] a){
		double[] b = new double[]{0d,0d};
		for (int i =0;i<a.length;++i){
			b[0]+=a[i][0];
			b[1]+=a[i][1];
		}
		b[0]/=(double) a.length;
		b[1]/=(double) a.length;
		return b;
	}
	  
	
	public static void main(String[] a){
		// the target is to have all points at the specified radius from the center
		EllipseFit ef = new EllipseFit();
		double[] coeffs = ef.getCoeffs();
		for (int i = 0; i< coeffs.length; ++i){
			System.out.println(String.format("Coeff %.1f",coeffs[i]));
		}
	}

}
