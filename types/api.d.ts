/**
 * Tipos da API do SIA, gerados de sia-server/src/main/resources/openapi.yaml.
 * NAO EDITE A MAO: rode `mvnw -pl sia-server test -Dtest=ApiTypesUpToDateTest`
 * (ou apague o arquivo e rode o teste) para regerar.
 */

export type HttpMethod = "GET" | "POST" | "DELETE";

export interface Error {
  error?: string;
}

export interface Health {
  status?: string;
  version?: string;
  auth?: "none" | "dev";
  plugins?: number;
  running?: number;
  failures?: string[];
}

export interface Param {
  name?: string;
  type?: "STRING" | "INTEGER" | "BOOLEAN" | "PATH" | "CHOICE";
  required?: boolean;
  sensitive?: boolean;
}

export interface Automation {
  id?: string;
  name?: string;
  description?: string;
  persistOutputs?: boolean;
  params?: Param[];
}

export interface Plugin {
  id: string;
  name: string;
  version: string;
  state: "DISCOVERED" | "VALIDATED" | "LOADED" | "INITIALIZED" | "ENABLED" | "DISABLED" | "UNLOADED" | "FAILED";
  automations: string[];
  ui: { entry: string; width: number; height: number };
}

export interface Run {
  id?: string;
  plugin?: string;
  automation?: string;
  actor?: string;
  status?: "QUEUED" | "RUNNING" | "SUCCESS" | "FAILURE" | "SKIPPED" | "CANCELLED";
  startedAt?: string | null;
  finishedAt?: string | null;
  trigger?: "api" | "schedule" | "workflow" | "manual";
  errorCode?: string | null;
  message?: string | null;
  params?: string[];
  outputs?: string[];
}

export interface Trigger {
  type: "cron" | "interval" | "event";
  expression?: string;
  zone?: string;
  every?: number;
}

export interface Schedule {
  id?: string;
  automation?: string;
  trigger?: Trigger;
  enabled?: boolean;
  nextFireAt?: string | null;
  owner?: string;
}

export interface RunRequest {
  automation: string;
  params?: Record<string, unknown>;
}

export interface RunAccepted {
  runId?: string;
}

export interface RunWaited {
  runId?: string;
  automation?: string;
  status?: "SUCCESS" | "FAILURE" | "SKIPPED" | "CANCELLED";
  errorCode?: string | null;
  message?: string | null;
  outputs?: Record<string, unknown>;
  durationMillis?: number | null;
}

export interface ScheduleRequest {
  automation: string;
  trigger: Trigger;
  params?: Record<string, string>;
}

export interface WorkflowRunRequest {
  inputs?: Record<string, unknown>;
}

export interface PluginPathRequest {
  path: string;
}

export interface PluginValidation {
  id?: string;
  version?: string;
  kind?: "java" | "process";
  mainClassPresent?: boolean;
}

export interface WorkflowRun {
  id?: string;
  name?: string;
  status?: "RUNNING" | "SUCCESS" | "PARTIAL" | "FAILURE";
  nodes?: Record<string, "PENDING" | "RUNNING" | "DONE" | "FAILED" | "SKIPPED">;
}

