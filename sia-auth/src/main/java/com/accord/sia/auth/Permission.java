package com.accord.sia.auth;

/** Acoes do SIA que exigem autorizacao explicita. */
public enum Permission {
  HEALTH_READ,
  PLUGIN_READ,
  RUN_READ,
  RUN_EXECUTE,
  RUN_CANCEL,
  PLUGIN_MANAGE,
  SCHEDULE_MANAGE
}
