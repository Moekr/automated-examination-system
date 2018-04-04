package com.moekr.aes.logic.api.impl.cobertura;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
public class CoberturaElement {
	private String name;
	private int numerator;
	private int denominator;
	private int ratio;
}
