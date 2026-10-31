/*
 * Copyright (C) 2010-2026 Tianjin Langhua Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.langhua.ofbiz.learningcurve;

import Jama.Matrix;

/**
 * The learning curve object which you may use it to draw graphics.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 *
 */
public class LearningCurve implements Cloneable {
	
	/** The slope value in the learning curve formula. */
	private double m_slope;

	/** The y-intercept value in the learning curve formula. */
	private double m_intercept;
	
	/** The least square value of the learning curve formula. */
	private double m_leastSquare;

	/** The qualified data of the learning curve. */
	private Matrix m_qualifiedData;

	/** The not qualified values of the learning curve. */
	private Matrix m_filteredData;
	
	/** The cumulative x value of the learning curve. */
	private double m_cumulativeX;
	
	/** The type of the learning curve. */
	private int m_type;
	
	/**
	 * public constructor
	 */
	public LearningCurve(double slope, double intercept, double leastSquare, Matrix qualifiedData, Matrix filteredData, double cumulativeX, int type) {
		m_slope = slope;
		m_intercept = intercept;
		setLeastSquare(leastSquare);
		setQualifiedData(qualifiedData);
		setFilteredData(filteredData);
		m_cumulativeX = cumulativeX;
		setType(type);
	}
	
	/**
	 * Public constructor
	 */
	public LearningCurve(double slope, double intercept, double leastSquare, int type) {
		this(slope, intercept, leastSquare, null, null, 0, type);
	}
	
	/**
	 * Static method to construct a LearningCurve instance.
	 * 
	 * @return a LearningCurve instance
	 */
	public static LearningCurve getInstance(double slope, double intercept, double leastSquare, int type) {
		return new LearningCurve(slope, intercept, leastSquare, type);
	}

	/**
	 * Static method to construct a LearningCurve instance.
	 * 
	 * @return a LearningCurve instance
	 */
	public static LearningCurve getInstance(double slope, double intercept, double leastSquare, Matrix qualifiedData, Matrix filteredData, double cumulativeX, int type) {
		return new LearningCurve(slope, intercept, leastSquare, qualifiedData, filteredData, cumulativeX, type);
	}

	/**
	 * Set the value of slope.
	 * 
	 * @param slope a double value to be stored in LearningCurve object
	 */
	public void setSlope(double slope) {
		m_slope = slope;
	}

	/**
	 * Get the value of slope.
	 * 
	 * @return a double value of the slope
	 */
	public double getSlope() {
		return m_slope;
	}

	/**
	 * Set the value of y-intercept.
	 * 
	 * @param intercept a double value of y-intercept to be stored in LearningCurve object
	 */
	public void setIntercept(double intercept) {
		m_intercept = intercept;
	}

	/**
	 * Get the value of y-intercept.
	 * 
	 * @return a double value of the y-intercept
	 */
	public double getIntercept() {
		return m_intercept;
	}

	/**
	 * Set the value of least square of the learning curve.
	 * 
	 * @param leastSquare a double value to be stored in LearningCurve object
	 */
	public void setLeastSquare(double leastSquare) {
		m_leastSquare = leastSquare;
	}

	/**
	 * Get the value least square.
	 * 
	 * @return a double value of the least square
	 */
	public double getLeastSquare() {
		return m_leastSquare;
	}

	/**
	 * Set the qualified data of the learning curve.
	 * 
	 * @param qualifiedData a Jama.Matrix which contains qualified data
	 */
	public void setQualifiedData(Matrix qualifiedData) {
		m_qualifiedData = qualifiedData;
	}

	/**
	 * Get the qualified data of the learning curve.
	 * 
	 * @return a Jama.Matrix value of qualified data of the learning curve
	 */
	public Matrix getQualifiedData() {
		return m_qualifiedData;
	}

	/**
	 * Set the NOT qualified data of the learning curve.
	 * 
	 * @param filteredData a Jama.Matrix which contains not qualified data
	 */
	public void setFilteredData(Matrix filteredData) {
		m_filteredData = filteredData;
	}

	/**
	 * Get the NOT qualified data of the learning curve.
	 * 
	 * @return a Jama.Matrix value of not qualified data of the learning curve
	 */
	public Matrix getFilteredData() {
		return m_filteredData;
	}

	/**
	 * Set the type of the learning curve.
	 * 
	 * @param type an integer which represents one of the types of learning curves
	 */
	public void setType(int type) {
		m_type = type;
	}

	/**
	 * Get the type of the learning curve.
	 * 
	 * @return an integer represents the type of the learning curve
	 */
	public int getType() {
		return m_type;
	}

	/**
	 * Set the cumulative X value of the learning curve.
	 * 
	 * @param cumulativeX a double value
	 */
	public void setCumulativeX(double cumulativeX) {
		this.m_cumulativeX = cumulativeX;
	}

	/**
	 * Get the cumulative X value of the learning curve.
	 * 
	 * @return a double value of cumulative X
	 */
	public double getCumulativeX() {
		return m_cumulativeX;
	}

	/**
	 * Override the clone method of java.lang.Object.
	 * 
	 * @return a cloned LearningCurve object of this instance
	 */
	@Override
	public LearningCurve clone() {
		return new LearningCurve(m_slope,
				m_intercept,
				m_leastSquare,
				m_qualifiedData,
				m_filteredData,
				m_cumulativeX,
				m_type);
	}
}