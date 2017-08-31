package timo.deakin.ellipsefit.nonlinlsqoptim;

/*
	Written by Timo Rantalainen 2017 (tjrantal at gmail dot com)
	Depends on JAMA (http://math.nist.gov/javanumerics/jama/) for matrix algebra
*/

import Jama.Matrix;

public class EllipseOptimAlgorithm extends GausNewtonAlgorithm{
	
	/**Constructor
		@param knownValues the known values to use in optimisation
	*/
	public EllipseOptimAlgorithm(Matrix knownValues, double convergenceLimit){
		super(knownValues);
		this.convergenceLimit = convergenceLimit;
	}
	
	/**Constructor
		@param knownValues the known values to use in optimisation
	*/
	public EllipseOptimAlgorithm(Matrix knownValues){
		super(knownValues);
	}
	
	/**
		The function to optimise, has to return residuals (i.e. f(x) - measured = 0)
		@param parameters being optimised entered as the parameter.
		@returns the values of the function at t
	*/
	@Override
	protected Matrix funct(Matrix parameters){
		double[] temp = new double[knownValues.getRowDimension()];
		for (int i = 0;i<knownValues.getRowDimension();++i){
			temp[i] = Math.pow(knownValues.get(i,0)-parameters.get(0,0),2d)/Math.pow(parameters.get(1,0),2d)+Math.pow(knownValues.get(i,1)-parameters.get(2,0),2d)/Math.pow(parameters.get(3,0),2d)-1d;
		}
		return new Matrix(temp,temp.length);
	}
	
	/**
		Jacobian of function to optimise. Could implement as numerical partial differentials
		, or as actual partial differentials.
		@parameters Parameters that are being optimised as the parameter
	*/
	@Override
	protected Matrix jac(Matrix parameters){
		double[][] temp  = new double[knownValues.getRowDimension()][4];
		for (int i = 0;i<knownValues.getRowDimension();++i){
			//derivative d/d0 (2a-2x)/b^2
			temp[i][0] = (2d*parameters.get(0,0)-2d*knownValues.get(i,0))/Math.pow(parameters.get(1,0),2d);
			//derivative d/d1 -2*(-a+x)^2/b^3
			temp[i][1] = (-2d*Math.pow(-parameters.get(0,0)+knownValues.get(i,0),2d))/Math.pow(parameters.get(1,0),3d);
			//derivative d/d2 (2c-2y)/d^2
			temp[i][2] = (2d*parameters.get(2,0)-2d*knownValues.get(i,1))/Math.pow(parameters.get(3,0),2d);
			//derivative d/d3 -2*(-c+y)^2/d^3
			temp[i][3] = (-2d*Math.pow(-parameters.get(2,0)+knownValues.get(i,1),2d))/Math.pow(parameters.get(3,0),3d);
		}
		return new Matrix(temp);
	}
}