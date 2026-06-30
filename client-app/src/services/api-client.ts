import axios, { AxiosInstance, AxiosResponse, AxiosRequestConfig } from 'axios';
import { EurekaClient } from './eureka';
import { JobSubmissionResponse, JobPollingResponse, NegativeExampleTarget, NegativeExamplesResponse } from '@/types/eureka';

export interface ApiCallOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  headers?: Record<string, string>;
  data?: unknown;
  params?: Record<string, string | number>;
  timeout?: number;
  useSecure?: boolean;
  responseType?: AxiosRequestConfig['responseType'];
}

export class ApiClient {
  private eurekaClient: EurekaClient;

  constructor(eurekaUrl: string) {
    this.eurekaClient = new EurekaClient(eurekaUrl);
  }

  /**
   * Call an API endpoint on a discovered service
   * Flow: Get service from Eureka → Extract hostname:port → Call hostname:port/endpoint
   */
  async callServiceEndpoint(
    serviceName: string,
    endpoint: string,
    options: ApiCallOptions = {}
  ): Promise<AxiosResponse> {
    const {
      method = 'GET',
      headers = {},
      data,
      params,
      timeout,
      useSecure = false,
      responseType,
    } = options;

    // Get service URL from Eureka (hostname:port)
    const baseUrl = await this.eurekaClient.getServiceUrlForCall(serviceName, useSecure);

    if (!baseUrl) {
      throw new Error(`Service ${serviceName} is not available or is not UP`);
    }

    // Prefer localhost when the page itself is loaded from localhost, to avoid container-only IPs
    const shouldForceLocalhost =
      import.meta.env.DEV ||
      (typeof window !== 'undefined' && ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname));

    const normalizedBaseUrl = shouldForceLocalhost
      ? (() => {
          try {
            const urlObj = new URL(baseUrl);
            urlObj.hostname = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
            return urlObj.toString().replace(/\/$/, '');
          } catch {
            return baseUrl;
          }
        })()
      : baseUrl;

    const path = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    const url = `${normalizedBaseUrl}${path}`;

    // Handle FormData: let browser set Content-Type with boundary
    const defaultHeaders: Record<string, string> = { ...headers };
    if (data instanceof FormData) {
      delete defaultHeaders['Content-Type'];
    } else {
      defaultHeaders['Content-Type'] = defaultHeaders['Content-Type'] || 'application/json';
    }

    const client: AxiosInstance = axios.create({
      ...(timeout !== undefined && { timeout }),
      headers: defaultHeaders,
    });

    try {
      const response = await client.request({
        url,
        method,
        data,
        params,
        responseType,
      });

      return response;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        throw new Error(
          `API call failed: ${error.message} - ${error.response?.status} ${error.response?.statusText}`
        );
      }
      throw error;
    }
  }

  async submitJob(
    serviceName: string,
    endpoint: string,
    params?: Record<string, string | number>
  ): Promise<JobSubmissionResponse> {
    const response = await this.callServiceEndpoint(serviceName, endpoint, {
      method: 'POST',
      params,
    });

    if (response.status === 202 && response.data) {
      return response.data as JobSubmissionResponse;
    }

    throw new Error(`Unexpected response status ${response.status} when submitting job`);
  }

  async pollJobStatus<T = unknown>(
    serviceName: string,
    jobId: string
  ): Promise<JobPollingResponse<T>> {
    const response = await this.callServiceEndpoint(serviceName, `/jobs/${jobId}`, {
      method: 'GET',
    });

    if (response.status === 200 && response.data) {
      return response.data as JobPollingResponse<T>;
    }

    throw new Error(`Unexpected response status ${response.status} when polling job`);
  }

  async answerSuspiciousQuestion(
    serviceName: string,
    jobId: string,
    genuine: boolean
  ): Promise<boolean> {
    try {
      const response = await this.callServiceEndpoint(
        serviceName,
        `/jobs/${jobId}/answer`,
        { method: 'POST', params: { genuine: genuine ? 'true' : 'false' } }
      );
      return response.status === 200;
    } catch {
      return false;
    }
  }

  async buildNegativeExamples(
    serviceName: string,
    filename: string,
    targets: NegativeExampleTarget[]
  ): Promise<NegativeExamplesResponse> {
    const response = await this.callServiceEndpoint(serviceName, '/negative-examples', {
      method: 'POST',
      data: { filename, targets },
    });
    return response.data as NegativeExamplesResponse;
  }

  async waitForJobCompletion<T = unknown>(
    serviceName: string,
    jobId: string,
    maxAttempts: number = 600
  ): Promise<JobPollingResponse<T>> {
    let attempts = 0;
    while (attempts < maxAttempts) {
      try {
        const jobStatus = await this.pollJobStatus<T>(serviceName, jobId);
        if (jobStatus.status === 'FINISHED' || jobStatus.status === 'FAILED') {
          return jobStatus;
        }
        await new Promise(resolve => setTimeout(resolve, 2000));
        attempts++;
      } catch (error) {
        throw error;
      }
    }
    throw new Error(`Job ${jobId} did not complete within ${maxAttempts * 2 / 60} minutes`);
  }
}

