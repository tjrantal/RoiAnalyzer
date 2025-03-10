package edu.deakin.timo.utils;
import Jama.*;
public class Utils{
	/*
		Calculate a nth order polynomial fit
		@param fitArray a N x 2 array with X-coordinates in the first dim, and Y-coordinates in the second
		@param nthOrder the order of the fit (1st order y  = a + bx)
		@return coefficients, the fit coefficients in a double array starting from the zeroth order coefficient
	*/

	public static double[] polynomialFit(double[][] fitArray, int nthOrder){

		double[][] powers = new double[fitArray.length][nthOrder+1];
		double[] bVals = new double[fitArray.length];
		int[] fitOrder = new int[]{0,1}; //fitForY ? new int[]{0,1} : new int[]{1,0};
		/*Create the matrix of nth order powers of x*/
		for (int i = 0; i< fitArray.length;++i){
			for (int j = 0;j<nthOrder+1;++j){
				powers[i][j] =Math.pow(fitArray[i][fitOrder[0]],(double)j);
			}
			bVals[i] = fitArray[i][fitOrder[1]];
		}
		/*Create the array of Y-values*/
		Matrix b = new Matrix(bVals,bVals.length);
		Matrix A = new Matrix(powers);
		double[] coefficients = new double[nthOrder+1];
		try{
			/*solve the coefficients with the least squares solution*/
			Matrix solution = A.solve(b);	
			/*return the coefficients in an array*/
			for (int j = 0;j<nthOrder+1;++j){
				coefficients[j] = solution.get(j,0);
			}
		}catch(Exception err){
			System.err.println("Matrix coefficients: " + err.getMessage());
			return null;
		}
		return coefficients;
	}

	//Return the vector magnitude
	public static double norm(double[] a){
		double sum = 0;
		for (int i = 0;i<a.length;++i){
			sum+=a[i]*a[i];
		}
		return Math.sqrt(sum);
	}

	//Return unit vector
	public static double[] getUnit(double[] a){
		double len = norm(a);
		double[] ret = new double[a.length];
		for (int i = 0;i<a.length;++i){
			ret[i]=a[i]/len;
		}
		return ret;
	}
}