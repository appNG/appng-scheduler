package org.appng.application.scheduler.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TriggerModel {

	private final String trigger;
	private final String description;
	private final String job;
	private final Date started;
	private final String data;
}
