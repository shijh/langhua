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

import java.util.Map;

import org.ofbiz.base.util.GeneralException;

import Jama.Matrix;

/**
 * The interface class which defines the methods will be called in Learning Curve estimate.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 *
 */
public interface I_OFBizLearningCurve {

	/** Type value of the Wright method. */
	public static final int WRIGHT = 1;
	
	/** Type value of the Crawford method. */
	public static final int CRAWFORD = 2;
	
	/**
	 * Get estimate cost.
	 * 
	 * @param leastSquare a double number between 0.0  1.0 which the estimate value must match, generally this number should be 0.95
	 * @param historyDate  a Jama.Matrix which contains the history data in a format of
	 * X1 Y1
	 * X2 Y2
	 * ...
	 * Xn Yn
	 * Xn is the unit number (a double number) of the work.
	 * Yn is the unit cost (a double number) consumed in a work(time or money, generally time is preferred).
	 * @param newUnitNumber a double number of new units to estimate
	 * @param context a java.util.Map containing the input parameters
	 * @param type an integer represents learning curve type
	 * 
	 * @return the total estimated cost of the new units
	 * 
	 * @throws org.ofbiz.base.util.GeneralException if any error was found
	 */
	double getEstimate(double leastSquare, Matrix historyData, double newUnitNumber, Map context, int type) throws GeneralException;
	
	/**
	 * Get estimate cost.
	 * 
	 * @param leastSquare a double number between 0.0  1.0 which the estimate value must match, generally this number should be 0.95
	 * @param historyDate  a Jama.Matrix which contains the history data in a format of
	 * X1 Y1
	 * X2 Y2
	 * ...
	 * Xn Yn
	 * Xn is the unit number (a double number) of the work.
	 * Yn is the unit cost (a double number) consumed in a work(time or money, generally time is preferred).
	 * @param context a java.util.Map containing the input parameters
	 * @param type learning curve type
	 * 
	 * @return the LearningCurve object
	 * 
	 * @throws org.ofbiz.base.util.GeneralException if any error was found
	 */
	LearningCurve getLearningCurve(double leastSquare, Matrix historyData, Map context, int type) throws GeneralException;

}
