package timo.deakin.ellipsefit.nonlinlsqoptim;

/*
	Written by Timo Rantalainen 2017 (tjrantal at gmail dot com)
	Depends on JAMA (http://math.nist.gov/javanumerics/jama/) for matrix algebra
*/

import Jama.Matrix;

public abstract class GausNewtonAlgorithm extends NonLinLSQOptimAlgorithm{
	
	/**Constructor
		@param knownValues the known values to use in optimisation
	*/
	public GausNewtonAlgorithm(Matrix knownValues){
		super(knownValues);
	}
	
	/**
		Run optimisation. Gauss-Newton algorithm implemented
		@param parameters, the parameters to optimise
		@return optimised parameters
	*/
	@Override
	public Matrix optimise(Matrix parameters){
		iterations = 0;
		diffParams= 100;
		resid  = 100;
		System.out.println(String.format("a %.1f b %.1f c %.1f d %.1f resid %.1f",parameters.get(0,0),parameters.get(1,0),parameters.get(2,0),parameters.get(3,0),(funct(parameters)).normF()));
		while (diffParams > convergenceLimit && diffResid > convergenceLimit && resid > convergenceLimit && iterations < maxIterations){
			
			Matrix functM = funct(parameters);
			resid = functM.normF();
			diffResid = Math.abs(prevResid-resid);
			prevResid = resid;
			Matrix jm = jac(parameters);
			Matrix jt = jm.transpose();
			Matrix jpseudoInv = ((jt.times(jm)).inverse()).times(jt);	//Calculate the pseudoinverse by the least squares method			
			Matrix parametersPlusOne = parameters.minus(jpseudoInv.times(functM));	//parameters s+1
			diffParams = parametersPlusOne.minus(parameters).normF();
			parameters = parametersPlusOne;
			++iterations;
			System.out.println(String.format("a %.1f b %.1f c %.1f d %.1f resid %.12f",parameters.get(0,0),parameters.get(1,0),parameters.get(2,0),parameters.get(3,0),resid));
		}
		return parameters;
	}
}