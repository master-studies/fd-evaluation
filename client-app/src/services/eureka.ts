import axios, { AxiosInstance } from 'axios';
import type {
  EurekaApplication,
  EurekaInstance,
  ServiceInfo,
  ServiceHealth,
} from '@/types/eureka';

export class EurekaClient {
  private client: AxiosInstance;
  private baseUrl: string;

  constructor(eurekaUrl: string) {
    // If using proxy path (starts with /), use it as-is
    // Otherwise, use the full URL
    if (eurekaUrl.startsWith('/')) {
      this.baseUrl = '';
    } else {
      this.baseUrl = eurekaUrl.replace(/\/$/, ''); // Remove trailing slash
    }
    
    this.client = axios.create({
      baseURL: this.baseUrl,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    });
  }

  /**
   * Get all registered applications from Eureka
   * Calls: /eureka/apps
   */
  async getAllApplications(): Promise<EurekaApplication[]> {
    try {
      const response = await this.client.get<any>('/eureka/apps', {
        headers: { 'Accept': 'application/json' },
      });
      
      const { data } = response;
      let applicationList: EurekaApplication[] = [];

      // Handle different response structures from Eureka
      if (data.applications?.application) {
        applicationList = Array.isArray(data.applications.application)
          ? data.applications.application
          : [data.applications.application];
      } else if (data.application) {
        applicationList = Array.isArray(data.application) ? data.application : [data.application];
      } else if (Array.isArray(data)) {
        applicationList = data;
      }

      // Filter out invalid entries
      return applicationList.filter((app): app is EurekaApplication => !!(app && app.name));
    } catch (error) {
      console.error('Error fetching applications from Eureka:', error);
      throw new Error(`Failed to fetch applications: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }

  async getServiceApplication(serviceName: string): Promise<EurekaApplication | null> {
    try {
      const applications = await this.getAllApplications();
      return applications.find(app => app.name.toUpperCase() === serviceName.toUpperCase()) || null;
    } catch (error) {
      console.error(`Error fetching service application for ${serviceName}:`, error);
      return null;
    }
  }

  async getServiceInstances(serviceName: string): Promise<EurekaInstance[]> {
    try {
      const serviceApp = await this.getServiceApplication(serviceName);
      if (!serviceApp?.instance) return [];
      return Array.isArray(serviceApp.instance) ? serviceApp.instance : [serviceApp.instance];
    } catch (error) {
      console.error(`Error fetching instances for service ${serviceName}:`, error);
      return [];
    }
  }


  async isServiceUp(serviceName: string): Promise<ServiceHealth> {
    const upperName = serviceName.toUpperCase();
    try {
      const instances = await this.getServiceInstances(serviceName);
      if (instances.length === 0) {
        return { serviceName: upperName, isUp: false, instances: [], lastChecked: new Date() };
      }

      const instance = instances[0];
      const serviceInfo: ServiceInfo = {
        instanceId: instance.instanceId,
        appName: instance.app,
        host: instance.hostName || instance.ipAddr,
        port: instance.port.$,
        status: instance.status,
        healthCheckUrl: instance.healthCheckUrl,
        homePageUrl: instance.homePageUrl,
        metadata: instance.metadata,
      };

      return {
        serviceName: upperName,
        isUp: instance.status === 'UP',
        instances: [serviceInfo],
        lastChecked: new Date(),
      };
    } catch (error) {
      console.error(`Error checking service health for ${serviceName}:`, error);
      return { serviceName: upperName, isUp: false, instances: [], lastChecked: new Date() };
    }
  }



  getServiceUrl(serviceName: string, useSecure: boolean = false): string {
    const protocol = useSecure ? 'https' : 'http';
    const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
    const port = typeof window !== 'undefined' && window.location.port ? `:${window.location.port}` : '';
    
    // Map service names to nginx proxy paths
    const serviceProxyMap: Record<string, string> = {
      'FD-DISCOVERY-SERVICE': '/fd-discovery',
      'SUCCINCTNESS-SERVICE': '/succinctness',
      'COVERAGE-SERVICE': '/coverage',
      'GENUINENESS-SERVICE': '/genuineness',
      'ENTROPY-SERVICE': '/entropy',
      'RELATIONAL_INFORMATION_CONTENT': '/entropy',
    };
    
    const upperName = serviceName.toUpperCase();
    const proxyPath = serviceProxyMap[upperName] || '';
    
    return `${protocol}://${host}${port}${proxyPath}`;
  }

  async getServiceUrlForCall(serviceName: string, useSecure: boolean = false): Promise<string | null> {
    try {
      const instances = await this.getServiceInstances(serviceName);
      const instance = instances[0];
      if (instance?.status !== 'UP') return null;
      return this.getServiceUrl(serviceName, useSecure);
    } catch (error) {
      console.error(`Error getting service URL for ${serviceName}:`, error);
      return null;
    }
  }
}

