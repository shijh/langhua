package org.langhua.opencms.misc;

import java.util.Comparator;



public class ComparatorCount implements Comparator{
	private static ComparatorCount comparatorCount = null;

	private ComparatorCount() {

	}

	public static synchronized ComparatorCount getInstance() {
		if (comparatorCount == null) {
			comparatorCount = new ComparatorCount();
			return comparatorCount;
		}
		return null;
	}

	
	public int compare(Object arg0, Object arg1) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	

}
