package com.example.camt.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single alias allocated to a payment type.
 *
 * @param aliasValue the alias identifier
 */
public record AliasAllocation(@JsonProperty("aliasValue") String aliasValue) {}
