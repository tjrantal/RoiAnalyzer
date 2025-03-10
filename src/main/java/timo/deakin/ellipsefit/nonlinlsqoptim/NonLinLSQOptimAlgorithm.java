package timo.deakin.ellipsefit.nonlinlsqoptim;

/*
	Written by Timo Rantalainen 2017 (tjrantal at gmail dot com)
	Depends on JAMA (http://math.nist.gov/javanumerics/jama/) for matrix algebra
*/

import Jama.Matrix;

public abstract class NonLinLSQOptimAlgorithm{
	
	protected Matrix knownValues;	//The measured values (e.g. measured force, coordinates).
	protected int iterations = 0;
	protected double  diffParams= 100;
	protected double  diffResid = 100;
	protected double	prevResid = 0;
	protected double	resid = 100;
	protected double convergenceLimit = 1e-12;//Math.ulp(1d);	//This is used to decide whether the algorithm has converged
	protected final static int maxIterations = 10000;	//Used to break infinite loop
	
	/**Constructor
		@param knownValues the known values to use in optimisation
	*/
	public NonLinLSQOptimAlgorithm(Matrix knownValues){
		this.knownValues  = knownValues;
	}
	
	/**
		Run optimisation
		@param parameters, the parameters to optimise
		@return optimised parameters
	*/
	public abstract Matrix optimise(Matrix parameters);
	
	
	/**
		The function to optimise, has to return residuals (i.e. f(x) - measured = 0)
		@param parameters being optimised entered as the parameter.
		@returns the values of the function at t
	*/
	protected abstract Matrix funct(Matrix parameters);
	
	/**
		Jacobian of function to optimise. Could implement as numerical partial differentials
		, or as actual partial differentials.
		@param Parameters that are being optimised as the parameter
		@returns Jacobian matrix
	*/
	protected abstract Matrix jac(Matrix parameters);
}