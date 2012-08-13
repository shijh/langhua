/*
 * This library is part of OFBiz-LearningCurve component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For the latest version about this module, please see the
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-learningcurve
 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.ofbiz.learningcurve;

import java.util.Locale;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;

import Jama.Matrix;

public class OFBizLearningCurveHandler implements I_OFBizLearningCurve {

	/** The resource to bundle error messages. */
	public static final String RESOURCE_ERROR = "LearningCurveErrorUiLabels";

	public static final String m_module = OFBizLearningCurveHandler.class
			.getName();

	/**
	 * Public constructor
	 */
	public OFBizLearningCurveHandler() {
		// empty constructor
	}

	/**
	 * Static method to construct an OFBizLearningCurveHandler instance.
	 * 
	 * @return an OFBizLearningCurveHandler instance
	 */
	public static OFBizLearningCurveHandler getInstance() {
		return new OFBizLearningCurveHandler();
	}

	/**
	 * @see cn.langhua.ofbiz.learningcurve.I_OFBizLearningCurve#getEstimate(double,
	 *      jama.Matrix, double, java.util.Map, int)
	 */
	public double getEstimate(double leastSquare,
			Matrix historyData, double newUnitNumber, Map context, int type)
			throws GeneralException {
		Locale locale = (Locale) context.get("locale");
		checkData(leastSquare, historyData, newUnitNumber, locale);
		LearningCurve learningCurve = getLearningCurve(leastSquare, historyData, context, type);
		if (type == I_OFBizLearningCurve.CRAWFORD) {
			double x0 = learningCurve.getCumulativeX();
			double T1 = Math.exp(learningCurve.getIntercept());
			double b = learningCurve.getSlope();
			double y0 = T1 * Math.pow(x0, b + 1);
			double y1 = T1 * Math.pow(x0 + newUnitNumber, b + 1);
			return y1 - y0;
		} else if (type == I_OFBizLearningCurve.WRIGHT) {
			double x0 = learningCurve.getCumulativeX();
			double T1 = learningCurve.getIntercept();
			double b = learningCurve.getSlope();
			double result = 0;
			for (int i=0; i<newUnitNumber; i++) {
				result += Math.exp(T1 + Math.log(x0 + i + 1) * b);
			}
			return result;
		}
		return -1.0;
	}

	/**
	 * @see cn.langhua.ofbiz.learningcurve.I_OFBizLearningCurve#getLearningCurve(double,
	 *      jama.Matrix, java.util.Map, int)
	 */
	public LearningCurve getLearningCurve(double leastSquare,
			Matrix historyData, Map context, int type) throws GeneralException {
		Locale locale = (Locale) context.get("locale");
		checkData(leastSquare, historyData, locale);
		double cumulativeX = getCumulativeX(historyData);
		if (type == I_OFBizLearningCurve.WRIGHT) {
			historyData = dataTransferToWrightMethod(historyData);
		} else if (type == I_OFBizLearningCurve.CRAWFORD) {
			historyData = dataTransferToCrawfordMethod(historyData);
		} else {
			String message = UtilProperties.getMessage(
					RESOURCE_ERROR, "UnkownMethodType", UtilMisc.toMap(
							"methodType", type), locale);
			Debug.logError(message, m_module);
			throw new GeneralException(message);
		}
		historyData = logValues(historyData);
		double currentLeastSquare = 0;
		int totalData = historyData.getRowDimension();
		Matrix paramsMatrix = null;
		Matrix filteredData = new Matrix(totalData, 2);
		Matrix qualifiedData = historyData.copy();
		
		while (true) {
			int rows = qualifiedData.getRowDimension();
			double[] yArray = new double[rows];
			double[][] xArray = new double[rows][2];
			double yTotal = 0;
			for (int i=0; i<rows; i++) {
				xArray[i][0] = 1.0;
				xArray[i][1] = qualifiedData.get(i, 0);
				yArray[i] = qualifiedData.get(i, 1);
				yTotal += yArray[i];
			}
			Matrix yMatrix = new Matrix(yArray, rows);
			Matrix xMatrix = new Matrix(xArray);
			paramsMatrix = xMatrix.solve(yMatrix);

			// calculate SSE: the error (or "unexplained") sum of squares
			Matrix eM = xMatrix.times(paramsMatrix).minus(yMatrix);
			double sre = eM.normF();
			double sse = sre * sre;
			
			// calculate SST: the total (corrected) sum of squares
			double yAverage = yTotal/rows;
			Matrix yAverageMatrix = new Matrix(rows, 1, yAverage);
			Matrix tM = yMatrix.minus(yAverageMatrix);
			double srt = tM.normF();
			double sst = srt * srt;
			
			// calculate the least square
			currentLeastSquare = 1 - sse/sst;
			
			// remove the data which is the max error
			if (currentLeastSquare >= leastSquare || qualifiedData.getRowDimension() <= 3) {
				break;
			}
			// get the max error
			double normInf = eM.normInf();
			double eArray[] = eM.getColumnPackedCopy();
			double newDataArray[][] = new double[rows - 1][2];
			for (int i=0, j=0; i<rows; i++) {
				if (normInf == Math.abs(eArray[i])) {
					filteredData.set(totalData - rows, 0, xArray[i][1]);
					filteredData.set(totalData - rows, 1, yArray[i]);
				} else {
					newDataArray[j][0] = xArray[i][1];
					newDataArray[j][1] = yArray[i];
					j++;
				}
			}
			qualifiedData = new Matrix(newDataArray);
		}
		double[] params = paramsMatrix.getColumnPackedCopy();
		if (qualifiedData.getRowDimension() == totalData) {
			filteredData = null;
		} else {
			filteredData = filteredData.getMatrix(0, totalData - qualifiedData.getRowDimension() - 1, 0, 1);
		}
		return new LearningCurve(params[1], params[0], currentLeastSquare, qualifiedData, filteredData, cumulativeX, type);
	}

	protected double getCumulativeX(Matrix historyData) {
		double xTotal = 0;
		for (int i=0; i<historyData.getRowDimension(); i++) {
			xTotal += Math.abs(historyData.get(i, 0));
		}
		return xTotal;
	}

	/**
	 * Transfer the history data to match Wright method.
	 * Example:
	 * Lot #   Number of Units     Lot Cost    Unit Curve X Plot Point    Unit Curve Y Plot Point
	 *   1            2               200                1                           100
	 *   2            3               285                3.5                          95
	 *   3            5               450                7.5                          90
	 *   
	 * @param historyData the Jama.Matrix of historyData
	 * 
	 * @return a Jama.Matrix matching Wright method
	 */
	public static Matrix dataTransferToWrightMethod(Matrix historyData) {
		double[][] dataArray = new double[historyData.getRowDimension()][2];
		double xTotal = 0;
		for (int i=0; i<historyData.getRowDimension(); i++) {
			double x = historyData.get(i, 0);
			if (i == 0) {
				if (x < 10) {
					dataArray[0][0] = (x == 1.0) ? 1.0 : x/2.0;
				} else {
					dataArray[0][0] = x/3.0;
				}
			} else {
				dataArray[i][0] = ((x == 1.0) ? 1.0 : x/2.0) + xTotal;
			}
			dataArray[i][1] = historyData.get(i, 1)/x;
			xTotal += x;
		}
		return new Matrix(dataArray);
	}

	/**
	 * Transfer the history data to match Crawford method.
	 * Example:
	 * Lot #   Number of Units     Lot Cost    Cumulative Average X Plot Point   Cumulative Average Y Plot Point
	 *   1            2               200                    2                                   100
	 *   2            3               285                    5                                    97
	 *   3            5               450                   10                                    93
	 * Note:  
	 *   Personally I think the 93 in the last line should be a mistake which caused the 
	 *   estimate result quite different from Wright method 
	 *   in the article: http://fast.faa.gov/pricing/98-30c18.htm
	 *   
	 * @param historyData the Jama.Matrix of historyData
	 * 
	 * @return a Jama.Matrix matching Crawford method
	 */
	public static Matrix dataTransferToCrawfordMethod(Matrix historyData) {
		double[][] dataArray = new double[historyData.getRowDimension()][2];
		double xTotal = 0;
		double yTotal = 0;
		for (int i=0; i<historyData.getRowDimension(); i++) {
			double x = historyData.get(i, 0);
			xTotal += x;
			dataArray[i][0] = xTotal;
			double y =historyData.get(i, 1);
			yTotal += y;
			dataArray[i][1] = yTotal/xTotal;
		}
		return new Matrix(dataArray);
	}

	/**
	 * Get a natural-logarithmed matrix.
	 * 
	 * @param historyData a Jama.Matrix of the original data 
	 * @return a Jama.Matrix all the data has been natural-logarithmed.
	 */
	public static Matrix logValues(Matrix historyData) {
		double[][] array = historyData.getArray();
		for (int i=0; i<historyData.getRowDimension(); i++) {
			array[i][0] = Math.log(historyData.get(i, 0));
			array[i][1] = Math.log(historyData.get(i, 1));
		}
		return new Matrix(array);
	}

	/**
	 * Check whether the data input are useful.
	 * 
	 * @param leastSquare a double number representing the desired least square
	 * @param historyData a Jama.Matrix contains the history data
	 * @param locale a java.util.Locale to output error message
	 * @throws GeneralException if any parameter is illegal
	 */
	protected void checkData(double leastSquare, Matrix historyData, Locale locale) throws GeneralException {
		if (leastSquare < 0.0 || leastSquare > 1.0) {
			if (locale != null) {
				String message = UtilProperties.getMessage(
						RESOURCE_ERROR, "LeastSquareOutOfRange", UtilMisc.toMap(
								"leastSquare", leastSquare), locale);
				Debug.logError(message, m_module);
				throw new GeneralException(message);
			} else {
				throw new GeneralException("Least square must be between 0 and 1. It's out of this range now.");
			}
		}
		if (historyData == null) {
			if (locale != null) {
				String message = UtilProperties.getMessage(
						RESOURCE_ERROR, "HistoryDataCannotBeNull", locale);
				Debug.logError(message, m_module);
				throw new GeneralException(message);
			} else {
				throw new GeneralException("The matrix of history data should not be null.");
			}
		}
		if (historyData.getRowDimension() < 3) {
			if (locale != null) {
				String message = UtilProperties.getMessage(
						RESOURCE_ERROR, "HistoryDataTooFew", UtilMisc.toMap(
								"historyData", historyData.getRowDimension()), locale);
				Debug.logError(message, m_module);
				throw new GeneralException(message);
			} else {
				throw new GeneralException("History data are too few. There should be at least 3 sets of data.");
			}
		}
	}

	/**
	 * Check whether the data are useful.
	 * 
	 * @param leastSquare a double number representing the desired least square
	 * @param historyData a Jama.Matrix contains the history data
	 * @param newUnitNumber a double number representing the amount of work to be estimated
	 * @param locale a java.util.Locale to output error message
	 * @throws GeneralException if any parameter is illegal
	 */
	protected void checkData(double leastSquare, Matrix historyData,
			double newUnitNumber, Locale locale) throws GeneralException {
		if (newUnitNumber <= 0.0) {
			if (locale != null) {
				String message = UtilProperties.getMessage(
						RESOURCE_ERROR, "NewUnitNumberMustBePositive", UtilMisc.toMap(
								"newUnitNumber", newUnitNumber), locale);
				Debug.logError(message, m_module);
				throw new GeneralException(message);
			} else {
				throw new GeneralException("New unit number must be positive.");
			}
		}
		checkData(leastSquare, historyData, locale);
	}

}