package timo.deakin.ellipsefit.polyfit;

import Jama.Matrix;	//Matrix algebra

public class PolynomialFit{
	/*
		Fit nth order polynomial to data points. N.B. Fit is done to X with respect to Y 
		@params a data for the fit. a[0] X-coordinates, x[1] Y-coordinates
		@params b order of the fit (i.e. the highest power)
		@params flip whether to do the flip w.r.t. to Y instead of X (which would be the normal way of doing it)
	*/
	public static Matrix getFit(double[][] a,int b, boolean flip){
		double[][] aa = new double[a.length][b+1];
		double[] bb = new double[a.length];
		int[] indices;
		if (flip){
			indices = new int[]{1,0};
		}else{
			indices = new int[]{0,1};
		}
		
		for (int i = 0; i<a.length;++i){
			for (int j = 0;j<=b;++j){
				aa[i][j] = Math.pow(a[i][indices[0]],(double) j);
			}
			bb[i] = a[i][indices[1]];
		}
		Matrix A = new Matrix(aa);
		Matrix B = new Matrix(bb,bb.length);
		return A.solve(B);
	}
}