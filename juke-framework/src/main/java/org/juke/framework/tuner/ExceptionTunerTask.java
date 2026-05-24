package org.juke.framework.tuner;


import org.juke.framework.exception.TunerGeneratedException;

public class ExceptionTunerTask extends TunerTask{
	private  String sequencedItem ;
	private  Exception exception ;
	
	public ExceptionTunerTask(Builder builder) {
		this.tuner=this.getClass();
		this.sequencedItem= builder.sequencedItem;
		this.exception = builder.exception;
	}
	public String getSequencedItem() {
		return sequencedItem;
	}
	public Exception getException() {
		return exception;
	}

	@Override
	public void execute(ProcessObject obj) throws Exception {
			if (exception !=null) {
				throw new TunerGeneratedException(exception);
			}
		
	}
	public static TunerTask getDefaultTuner(String sequencedItem) {
		return new ExceptionTunerTask.Builder(sequencedItem, "Exception").build();
	}

	public static class Builder{
		private String sequencedItem = null;

		private  Exception exception ;
		
		public Builder(String sequencedItem, String exception ) {
			
			this.sequencedItem=sequencedItem;
			this.exception=ServerResponseMock.getException(exception);
		}

		
	 

		public Builder sequencedItem(String sequencedItem) {
			this.sequencedItem = sequencedItem;
			return this;
		}

		public Builder exception(Exception exception ) {
			this.exception=exception ;
			return this;
		}

		public ExceptionTunerTask build() {
			ExceptionTunerTask tuner= new ExceptionTunerTask(this);
			TunerTask.add(tuner, this.sequencedItem);
			return tuner;
		}

		
		
	}
	
	
}
