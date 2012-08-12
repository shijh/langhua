/*
 * This library is part of OFBiz-HTMLReport Component of Langhua
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
 * For the latest version about this component, please see the
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-htmlreport/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.ofbiz.htmlreport;

import org.safehaus.uuid.UUID;

/**
 * Identifies a class that can be used as a report thread .<p>
 * 
 * @author Michael Emmerich
 * @author Shi Yusen, shiys@langhua.cn
 *  
 */
public interface I_OFBizReportThread {

    /**
     * Starts the report thread.<p>
     */
    void start();

    /**
     * Returns the UUID of this report thread.<p>
     * 
     * @return the UUID of this report thread
     */
    UUID getUUID();

}
