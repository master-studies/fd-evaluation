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

export type ServiceType = 'FD_DISCOVERY' | 'SUCCINCTNESS' | 'COVERAGE' | 'GENUINENESS' | 'RELATIONAL_INFORMATION_CONTENT';

export interface JobSubmissionResponse {
  jobId: string;
  status: JobStatus;
  serviceType?: ServiceType; // Optional, for backend compatibility
}

export interface JobPollingResponse<T = unknown> {
  jobId: string;
  status: JobStatus;
  serviceType?: ServiceType; // Optional, for backend compatibility
  result?: T;
  error?: string;
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

