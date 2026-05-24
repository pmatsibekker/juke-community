package com.example;

import org.springframework.stereotype.Service;

import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GreetingServiceImpl implements IGreetingsService {
	private static final String template = "Hello, %s!";

	private final AtomicLong counter = new AtomicLong();
	@Override
	public Greeting greeting(String name) {
		Formatter formatter = new Formatter(new StringBuilder(), Locale.US);
		return new Greeting(counter.incrementAndGet(), formatter.format(template, name).toString());
	}

}
