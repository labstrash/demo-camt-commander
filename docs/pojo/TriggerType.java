package com.example.camt.contract;

/**
 * How the report was triggered: the scheduled Quartz job, an on-demand request, or an
 * external system pushing its own data (e.g. the EXT balance use case).
 */
public enum TriggerType {
    SCHEDULED,
    ON_DEMAND,
    EXTERNAL
}
