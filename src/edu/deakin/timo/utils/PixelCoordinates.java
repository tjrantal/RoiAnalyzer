/**
	A class to store the original, and rotated ROI pixel coordinates, and the subregion divisions. Subregion indices are assigned in row major order. Working out the rotation angle, and the rotation are also implemented in this class
*/

package edu.deakin.timo.utils;
import ij.IJ;
import java.util.*;	//Vector, Collections
import edu.deakin.timo.detectEdges.EdgeDetector;
import edu.deakin.timo.detectEdges.DetectedEdge;
public class PixelCoordinates{
	public double[][] coordinates;
	public double[][] rotatedCoordinates;
	public double[] centreCoordinates;
	public int roiPixels;
	public double angle;
	
	/**
		Constructor
		@param mask the byte mask for calculating ROI pixel coordinates for
		@param width the width of the image the mask corresponds to
		@param height the height of the image the mask corresponds to
	*/
	
	public PixelCoordinates(byte[] mask,int width, int height,int rotationSelection){
		roiPixels = 0;
		for (int i = 0; i<mask.length;++i){
			if (mask[i]==1){
				++roiPixels;
			}
		}
		coordinates = new double[roiPixels][2];
		centreCoordinates = new double[2];
		roiPixels = 0;
		for (int j = 0;j< height;j++){
			for (int i = 0; i < width;i++){
				if (mask[i+j*width] == 1){
					coordinates[roiPixels][0] = i;	//x-coordinate
					coordinates[roiPixels][1] = j;	//y-coordinate
					centreCoordinates[0]+=(double) i;
					centreCoordinates[1]+=(double) j;
					++roiPixels;
				}
			}
		}
		centreCoordinates[0]/=(double)roiPixels;
		centreCoordinates[1]/=(double)roiPixels;
		
		angle = 0;
		double[][] pixelsForRotation=  null;
		double[][][] sideCoords;
		switch (rotationSelection){
			case 0:
				//Get rotationAngle based on all Roi Pixels
				pixelsForRotation = coordinates;
				//IJ.log("All");
				break;
			case 1:
				//Get rotation angle based on top row of pixels
				sideCoords = getRoiSideCoordinates(centreCoordinates,mask,width,height);
				pixelsForRotation = sideCoords[0];
				//IJ.log("Top");
				break;
			case 2:
				//Get rotation angle based on bottom row of pixels
				sideCoords = getRoiSideCoordinates(centreCoordinates,mask,width,height);
				pixelsForRotation = sideCoords[2];
				//IJ.log("Bottom");
				break;
		}
		angle = getRotationAngle(pixelsForRotation);
		//IJ.log("Angle "+angle);
			
		
		//IJ.log("Angle "+angle/Math.PI*180d);
		//Calculate rotated coordinates
		rotatedCoordinates = new double[coordinates.length][2];
		for (int i = 0;i<coordinates.length;++i){
			double[] temp = rotateVector(new double[]{coordinates[i][0],coordinates[i][1]},angle);
			rotatedCoordinates[i][0] = temp[0];
			rotatedCoordinates[i][1] = temp[1];
		}
	}
	
	/**
		Get rotation angle for a ROI to align the top row  with the horizon
		
		@param coordinates N x 2 array of ROI pixel coordinates
		@param centreCoordinates ROI centre coordinates
		@return required rotation angle
		EdgeDetector
	*/
	private double[][][] getRoiSideCoordinates(double[] centreCoordinates,byte[] mask,int width, int height){
		//IJ.log("Create EdgeDetector");
		//DetectedEdge test = new DetectedEdge(new Vector<Integer>(),new Vector<Integer>());
		EdgeDetector edge = new EdgeDetector(mask,width,height);
		//IJ.log("Vector Detected Edge");
		Vector<DetectedEdge> edges = edge.edges;
		return getSidePixels(edges.get(0), centreCoordinates);	//The tangent of the rotation angle is coeffs[1]/1
	}
	
	
	/**
		Get rotation angle for a ROI to align the long axis with horizon
		@param coordinates N x 2 array of ROI pixel coordinates
		@return required rotation angle
	*/
	private double getRotationAngle(double[][] coordinates){
		double[] coeffs = Utils.polynomialFit(coordinates, 1);
		return Math.atan(coeffs[1]);	/*The tangent of the rotation angle is coeffs[1]/1*/
	}
	
	/**
		Rotate a 2D-vector with an angle. Positive angle leads into counter clockwise rotation
		@param vector a 2D vector to be rotated
		@param angle angle in radians to rotate
		@return rotated the rotated 2D vector
	*/
	private double[] rotateVector(double[] vector,double angle){
		double[] rotated = new double[2];
		rotated[0] = Math.cos(-angle)*vector[0]-Math.sin(-angle)*vector[1];
		rotated[1] = Math.sin(-angle)*vector[0]+Math.cos(-angle)*vector[1];
		return rotated;	
	}
	
	
	/**
		Determine the corners of the edge. Helper function for getRotationAngleTopRow
		@param edge The coordinates of the edge for corner detection
		@param centreCoords, the coordinates of the centre of the ROI the edge belongs to
		@return corners 4 by 2 array with x-, and y- coordinates of the corners
	
	*/
	public static double[][][] getSidePixels(DetectedEdge edge, double[] centreCoords){


		int[][] corners = null;
		/*Calculate the roi coordinates in polar coordinates*/
		double r,theta,a,b;
		Double[][] polar = new Double[edge.iit.size()][4];	/*0 r, 1 theta, 2 x, 3 y*/
		//IJ.log("Get polar coords "+edge.iit.size());
		for (int i=0; i<edge.iit.size();++i){
			polar[i][2] = new Double((double) edge.iit.get(i));
			polar[i][3] = new Double((double) edge.jiit.get(i));
			a = polar[i][2]-centreCoords[0];
			b = polar[i][3]-centreCoords[1];
			r = Math.sqrt(Math.pow(a,2d)+Math.pow(b,2d));
			theta = Math.atan2(b,a);
			polar[i][0] = r;
			polar[i][1] = theta;
			
		}
		/*sort the coordinates, theta 0 = towards right, theta increases clock-wise in ImageJ image (where y increases towards bottom!*/
		/**Prepare a comparator*/
		Comparator<Double[]> comparator = new Comparator<Double[]>() {
			@Override
			public int compare(Double[] o1, Double[] o2) {
				return o1[1].compareTo(o2[1]);
			}
		};
		//IJ.log("Sort polar coords");
		Arrays.sort(polar,comparator);	/**Sort the polar coordinates into ascending order according to theta*/
		/**Look for the four corners, take the maximum r in the four sectors -pi to -pi/2; -pi/2 to 0; 0 to pi/2; pi/2 to pi*/
		int ind = 0;
		double[][] polarCorners = new double[4][];
		//IJ.log("Find polar corners");
		for (int i = 0;i<4;++i){
			double maxR = -1;
			double maxTheta = 0;
			double maxInd = -1;
			while (ind < polar.length && polar[ind][1] < ((2*Math.PI*((double)i+1d)/4d)-Math.PI)){
				if (polar[ind][0] > maxR){
					maxR = polar[ind][0];
					maxTheta = polar[ind][1];
					maxInd = ind;
				}
				++ind;
			}
			polarCorners[i] = new double[]{maxR,maxTheta,maxInd};	
			//IJ.log("found corner "+i+" x "+ polar[(int)polarCorners[i][2]][2] + " y "+polar[(int)polarCorners[i][2]][3]);
		}
		
		//Get the side coordinates
		double[][][] sideCoordinates = new double[4][][];
		int cnt = 0;
		//The last side has to be handled manually
		for (int i = 0;i<polarCorners.length-1;++i){
			sideCoordinates[i] = new double[(int) (polarCorners[i+1][2]-polarCorners[i][2]+1)][2];
			cnt = 0;
			for (int j = (int) polarCorners[i][2]; j<= (int) polarCorners[i+1][2];++j){
				sideCoordinates[i][cnt][0] = polar[j][2];
				sideCoordinates[i][cnt][1] = polar[j][3];
				++cnt;
			}
		}
		//Final side
		sideCoordinates[3] = new double[(int) (polar.length-polarCorners[3][2]+polarCorners[0][2]+1)][2];
		cnt = 0;
		for (int j = (int) polarCorners[3][2]; j<polar.length;++j){
			sideCoordinates[3][cnt][0] = polar[j][2];
			sideCoordinates[3][cnt][1] = polar[j][3];
			++cnt;
		}
		for (int j = 0 ; j<=(int) polarCorners[0][2];++j){
			sideCoordinates[3][cnt][0] = polar[j][2];
			sideCoordinates[3][cnt][1] = polar[j][3];
			++cnt;
		}
		return sideCoordinates;
	}
}