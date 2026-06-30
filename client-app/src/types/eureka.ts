export interface EurekaInstance {
  instanceId: string;
  hostName: string;
  app: string;
  ipAddr: string;
  status: 'UP' | 'DOWN' | 'STARTING' | 'OUT_OF_SERVICE' | 'UNKNOWN';
  overriddenstatus: string;
  port: {
    $: number;
    '@enabled': string;
  };
  securePort: {
    $: number;
    '@enabled': string;
  };
  countryId: number;
  dataCenterInfo: {
    '@class': string;
    name: string;
  };
  leaseInfo: {
    renewalIntervalInSecs: number;
    durationInSecs: number;
    registrationTimestamp: number;
    lastRenewalTimestamp: number;
    evictionTimestamp: number;
    serviceUpTimestamp: number;
  };
  metadata?: Record<string, string>;
  homePageUrl: string;
  statusPageUrl: string;
  healthCheckUrl: string;
  vipAddress: string;
  secureVipAddress: string;
  isCoordinatingDiscoveryServer: string;
  lastUpdatedTimestamp: string;
  lastDirtyTimestamp: string;
  actionType: string;
}

export interface EurekaApplication {
  name: string;
  instance?: EurekaInstance | EurekaInstance[];
}

export interface EurekaApplications {
  versions__delta?: string;
  apps__hashcode?: string;
  application?: EurekaApplication | EurekaApplication[];
}

// Job Queue Types
export type JobStatus = 'NEW' | 'RUNNING' | 'FINISHED' | 'FAILED';

export type ServiceType = 'FD_DISCOVERY' | 'SUCCINCTNESS' | 'COVERAGE' | 'GENUINENESS' | 'RELATIONAL_INFORMATION_CONTENT' | 'EVALUATION_PATTERNS';

export interface JobSubmissionResponse {
  jobId: string;
  status: JobStatus;
  serviceType?: ServiceType;
}

export interface SuspiciousQuestion {
  lhs: string[];
  rhs: string;
  text: string;
}

export interface CompletedRhsEntry {
  rhs: string;
  antichain: string[];
}

export interface EvalProcessingState {
  phase: 'processing' | 'awaiting_input';
  completedRhs: CompletedRhsEntry[];
  currentRhs: string | null;
  question: SuspiciousQuestion | null;
}

export interface JobPollingResponse<T = unknown> {
  jobId: string;
  status: JobStatus;
  serviceType?: ServiceType;
  result?: T;
  error?: string;
  // evaluation-patterns specific
  resultCsv?: string;
  processingState?: EvalProcessingState | null;
}

// Negative Examples types
export interface NegativeExampleTarget {
  lhs: string[];
  rhs: string;
}

export interface NegativeExampleRow {
  values: string[];
  type: 'base' | 'negative' | 'existing';
}

export interface NegativeExampleEntry {
  lhs: string[];
  rhs: string;
  rows: NegativeExampleRow[];
}

export interface NegativeExamplesResponse {
  columnNames: string[];
  negativeExamples: NegativeExampleEntry[];
}

export interface EurekaResponse {
  applications: EurekaApplications;
}

export interface ServiceInfo {
  instanceId: string;
  appName: string;
  host: string;
  port: number;
  status: string;
  healthCheckUrl: string;
  homePageUrl: string;
  metadata?: Record<string, string>;
}

export interface ServiceHealth {
  serviceName: string;
  isUp: boolean;
  instances: ServiceInfo[];
  lastChecked: Date;
}

