package org.juke.framework.support;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ISampleService {

	public HashMap<String, BigDecimal> getMyDataMap(BigDecimal[] values, String keyNames);
	
	public BigDecimal[]  getMyDataArrayReverse(Map<String, BigDecimal> values);
	
	public Set<BigDecimal> getMyDataSet(BigDecimal[] values);
	
	public BigDecimal[]  getMyDataArrayReverse2(Set<BigDecimal> values);
	
	public List<HashMap<String, BigDecimal>> getMyDataMapAsList(BigDecimal[] values, String keyNames);	
	public List<HashMap<String, BigDecimal>> getMyDataMapAsList(List<BigDecimal> values, String keyNames);	
	
	public Double[] fromSimpleDoubleArray(double[] d1);

	public double[] toSimpleDoubleArray(Double[] d1);

}
