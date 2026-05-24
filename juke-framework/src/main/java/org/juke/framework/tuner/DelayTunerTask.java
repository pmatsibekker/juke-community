package org.juke.framework.tuner;

import org.juke.framework.exception.JukeTunerException;

public class DelayTunerTask extends TunerTask {
	private  String sequencedItem = null;

	private  long delay = 0;

	private DelayTunerTask(Builder builder) {
		this.tuner=this.getClass();
		this.sequencedItem=builder.sequencedItem;
		this.delay=builder.delay;
	}
	public String getSequencedItem() {
		return sequencedItem;
	}

	public long getDelay() {
		return delay;
	}

	@Override
	public void execute(ProcessObject obj) throws Exception {
		if (getParticipants().get(this.getClass().getCanonicalName()).contains(this.sequencedItem)) {
			if (getDelay() > 0) {
				try {
					Thread.currentThread().sleep(this.delay);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new JukeTunerException(e);
				}
			}
		
		}
		//else ignore it
	}
	public static TunerTask getDefaultTuner(String sequencedItem) {
		
		
		return new DelayTunerTask.Builder(sequencedItem, 0).build();
	}
	
	public static class Builder{
		private String sequencedItem = null;

		private long delay = 0;
		public Builder(String sequencedItem, long delay) {
		
			this.sequencedItem=sequencedItem;
			this.delay=delay;
		}

	 

		public Builder sequencedItem(String sequencedItem) {
			this.sequencedItem = sequencedItem;
			return this;
		}

		public Builder delay(long delay) {
			this.delay=delay;
			return this;
		}

		public DelayTunerTask build() {
			DelayTunerTask tuner = new DelayTunerTask(this);
			
			TunerTask.add(tuner, this.sequencedItem);
			return tuner;
		}

		
		
	}

}
