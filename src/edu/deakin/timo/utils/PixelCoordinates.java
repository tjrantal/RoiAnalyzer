/**
	A class to store the original, and rotated ROI pixel coordinates, and the subregion divisions. Subregion indices are assigned in row major order. Working out the rotation angle, and the rotation are also implemented in this class
*/

package edu.deakin.timo.utils;
import ij.IJ;
import edu.deakin.timo.detectEdges.*;
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
	
	public PixelCoordinates(byte[] mask,int width, int height){
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
		//Get rotationAngle based on all Roi Pixels
		//angle = getRotationAngle(coordinates);
		
		//Get rotation angle based on top row of pixels
		angle = getRotationAngleTopRow(coordinates,centreCoordinates);
		
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
	*/
	private double getRotationAngle(double[][] coordinates,double[] centreCoordinates){
		EdgeDetector edge = new EdgeDetector(segmentationMask);
		Vector<DetectedEdge> edges = edge.edges;
	
		double[] coeffs = Utils.polynomialFit(coordinates, 1);
		return Math.atan(coeffs[1]);	/*The tangent of the rotation angle is coeffs[1]/1*/
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
}