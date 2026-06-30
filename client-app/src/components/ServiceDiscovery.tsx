import { useState, useEffect, useRef, ChangeEvent } from 'react';
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { ApiClient } from '@/services/api-client';
import { EurekaClient } from '@/services/eureka';
import { JobStatus, ServiceType } from '@/types/eureka';
import { Loader2, Eye, Calculator, BarChart2, Target, Info, Network } from 'lucide-react';
import { SuspiciousQuestion, CompletedRhsEntry, NegativeExamplesResponse } from '@/types/eureka';

interface ServiceDiscoveryProps {
  eurekaUrl: string;
}

// Constants
const SERVICE_NAME = 'FD-DISCOVERY-SERVICE';
const DATASETS_ENDPOINT = '/dataset/list';

// Service names for calculations
const SERVICES = {
  SUCCINCTNESS: 'SUCCINCTNESS-SERVICE',
  COVERAGE: 'COVERAGE-SERVICE',
  GENUINENESS: 'GENUINENESS-SERVICE',
  RELATIONAL_INFORMATION_CONTENT: 'RELATIONAL_INFORMATION_CONTENT',
  EVALUATION_PATTERNS: 'EVALUATION-PATTERNS-SERVICE',
} as const;

// Service type mapping to service names
const SERVICE_TYPE_MAP: Record<ServiceType, string> = {
  FD_DISCOVERY: SERVICE_NAME,
  SUCCINCTNESS: SERVICES.SUCCINCTNESS,
  COVERAGE: SERVICES.COVERAGE,
  GENUINENESS: SERVICES.GENUINENESS,
  RELATIONAL_INFORMATION_CONTENT: SERVICES.RELATIONAL_INFORMATION_CONTENT,
  EVALUATION_PATTERNS: SERVICES.EVALUATION_PATTERNS,
};

// Types
interface Dataset {
  name: string;
  [key: string]: unknown;
}

interface ExtractRule {
  lhs: string;
  rhs: string;
  indicesStr?: string; // Raw indices string like "[0, 1] --> 2"
}

interface CalculationResult {
  values: string[];
  attributeId: string;
  score?: number;
}

interface CalculationState {
  isCalculating: boolean;
  results: CalculationResult[];
  error: string | null;
}

interface EntropyOptions {
  identifyOnes: boolean;
  considerSubtables: boolean;
  randomizedApproach: {
    enabled: boolean;
    runs: number;
  };
  closure: boolean;
  saveResult: {
    enabled: boolean;
    filename: string;
  };
}

interface EvalPatternsState {
  show: boolean;
  isRunning: boolean;
  jobId: string | null;
  completedRhs: CompletedRhsEntry[];
  currentRhs: string | null;
  question: SuspiciousQuestion | null;
  resultCsv: string | null;
  error: string | null;
  negativeExamplesLoading: boolean;
  negativeExamplesResult: NegativeExamplesResponse | null;
  negativeExamplesError: string | null;
}

const createInitialEvalPatternsState = (): EvalPatternsState => ({
  show: false,
  isRunning: false,
  jobId: null,
  completedRhs: [],
  currentRhs: null,
  question: null,
  resultCsv: null,
  error: null,
  negativeExamplesLoading: false,
  negativeExamplesResult: null,
  negativeExamplesError: null,
});

// Job State for individual service
interface ServiceJobState {
  jobId: string | null;
  status: JobStatus | null;
  isPolling: boolean;
  error: string | null;
}

// Job State Map for all services
type JobStateMap = Record<ServiceType, ServiceJobState>;

const createInitialJobStates = (): JobStateMap => ({
  FD_DISCOVERY: { jobId: null, status: null, isPolling: false, error: null },
  SUCCINCTNESS: { jobId: null, status: null, isPolling: false, error: null },
  COVERAGE: { jobId: null, status: null, isPolling: false, error: null },
  GENUINENESS: { jobId: null, status: null, isPolling: false, error: null },
  RELATIONAL_INFORMATION_CONTENT: { jobId: null, status: null, isPolling: false, error: null },
  EVALUATION_PATTERNS: { jobId: null, status: null, isPolling: false, error: null },
});

const createInitialCalculations = (): Record<string, CalculationState> => ({
  succinctness: { isCalculating: false, results: [], error: null },
  coverage: { isCalculating: false, results: [], error: null },
  genuineness: { isCalculating: false, results: [], error: null },
  relationalInformationContent: { isCalculating: false, results: [], error: null },
});

const createInitialEntropyOptions = (): EntropyOptions => ({
  identifyOnes: false,
  considerSubtables: false,
  randomizedApproach: { enabled: false, runs: 100000 },
  closure: false,
  saveResult: { enabled: true, filename: '' },
});

interface DatasetDetailState {
  extractRules: ExtractRule[];
  calculations: Record<string, CalculationState>;
  currentFilename: string | null;
  rulesError: string | null;
  isLoadingRules: boolean;
  jobStates: JobStateMap;
  currentPage: number;
  showEntropyDialog: boolean;
  entropyOptions: EntropyOptions;
}

export function ServiceDiscovery({ eurekaUrl }: ServiceDiscoveryProps) {
  const [datasets, setDatasets] = useState<Dataset[]>(() => {
    const stored = localStorage.getItem('fd-datasets');
    return stored ? JSON.parse(stored) : [];
  });
  const [selectedDatasets, setSelectedDatasets] = useState<string[]>(() => {
    const stored = localStorage.getItem('fd-selectedDatasets');
    return stored ? JSON.parse(stored) : [];
  });
  const [datasetStates, setDatasetStates] = useState<Record<string, DatasetDetailState>>(() => {
    const stored = localStorage.getItem('fd-datasetStates');
    return stored ? JSON.parse(stored) : {};
  });
  const [isLoadingDatasets, setIsLoadingDatasets] = useState(false);
  const [datasetsError, setDatasetsError] = useState<string | null>(null);
  const [datasetsCurrentPage, setDatasetsCurrentPage] = useState(1);
  const hasFetchedDatasets = useRef(false);
  const detailsRef = useRef<HTMLDivElement>(null);
  const entropyDialogRef = useRef<HTMLDivElement>(null);
  const itemsPerPage = 10;
  const datasetsPerPage = 5;
  const pollIntervalRefs = useRef<Record<string, Record<ServiceType, NodeJS.Timeout | null>>>({});
  const evalPollRefs = useRef<Record<string, NodeJS.Timeout | null>>({});

  const [evalPatternsStates, setEvalPatternsStates] = useState<Record<string, EvalPatternsState>>({});

  const eurekaClient = new EurekaClient(eurekaUrl);
  const apiClient = new ApiClient(eurekaUrl);

  // Upload state
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  // Entropy upload per-dataset state
  const [entropyFiles, setEntropyFiles] = useState<Record<string, File | null>>({});
  const [isUploadingEntropy, setIsUploadingEntropy] = useState<Record<string, boolean>>({});
  const [entropyDatasets, setEntropyDatasets] = useState<string[]>([]);
  const [entropyDatasetsError, setEntropyDatasetsError] = useState<string | null>(null);
  const [isLoadingEntropyDatasets, setIsLoadingEntropyDatasets] = useState(false);
  const [tableFileNamesByDataset, setTableFileNamesByDataset] = useState<Record<string, string | null>>({});
  const [fileNameMismatch, setFileNameMismatch] = useState<Record<string, boolean>>({});
  const [serviceAvailability, setServiceAvailability] = useState<Record<string, boolean>>({
    [SERVICES.SUCCINCTNESS]: true,
    [SERVICES.COVERAGE]: true,
    [SERVICES.GENUINENESS]: true,
    [SERVICES.RELATIONAL_INFORMATION_CONTENT]: true,
    [SERVICES.EVALUATION_PATTERNS]: true,
  });
  const [entropyMinimized, setEntropyMinimized] = useState<Set<string>>(() => new Set());
  const [minimizedDatasets, setMinimizedDatasets] = useState<Set<string>>(() => {
    const stored = localStorage.getItem('fd-minimizedDatasets');
    return stored ? new Set(JSON.parse(stored)) : new Set();
  });

  // Check service availability on mount
  useEffect(() => {
    const checkServices = async () => {
      const checks = await Promise.all([
        eurekaClient.isServiceUp(SERVICES.SUCCINCTNESS),
        eurekaClient.isServiceUp(SERVICES.COVERAGE),
        eurekaClient.isServiceUp(SERVICES.GENUINENESS),
        eurekaClient.isServiceUp(SERVICES.RELATIONAL_INFORMATION_CONTENT),
        eurekaClient.isServiceUp(SERVICES.EVALUATION_PATTERNS),
      ]);
      setServiceAvailability({
        [SERVICES.SUCCINCTNESS]: checks[0].isUp,
        [SERVICES.COVERAGE]: checks[1].isUp,
        [SERVICES.GENUINENESS]: checks[2].isUp,
        [SERVICES.RELATIONAL_INFORMATION_CONTENT]: checks[3].isUp,
        [SERVICES.EVALUATION_PATTERNS]: checks[4].isUp,
      });
    };
    checkServices();
  }, []);

  // Persist datasets to localStorage
  useEffect(() => {
    localStorage.setItem('fd-datasets', JSON.stringify(datasets));
  }, [datasets]);

  // Persist selectedDatasets to localStorage
  useEffect(() => {
    localStorage.setItem('fd-selectedDatasets', JSON.stringify(selectedDatasets));
  }, [selectedDatasets]);

  // Persist datasetStates to localStorage
  useEffect(() => {
    localStorage.setItem('fd-datasetStates', JSON.stringify(datasetStates));
  }, [datasetStates]);

  // Persist minimizedDatasets to localStorage
  useEffect(() => {
    localStorage.setItem('fd-minimizedDatasets', JSON.stringify(Array.from(minimizedDatasets)));
  }, [minimizedDatasets]);

  // Helper functions
  const updateDatasetState = (
    datasetName: string,
    updater: (state: DatasetDetailState) => DatasetDetailState
  ) => {
    setDatasetStates(prev => {
      const current = prev[datasetName];
      if (!current) return prev;
      return { ...prev, [datasetName]: updater(current) };
    });
  };

  const updateCalculationState = (
    datasetName: string,
    type: string,
    updates: Partial<CalculationState>
  ) => {
    updateDatasetState(datasetName, state => ({
      ...state,
      calculations: {
        ...state.calculations,
        [type]: { ...state.calculations[type], ...updates },
      },
    }));
  };

  const updateJobState = (
    datasetName: string,
    serviceType: ServiceType,
    updates: Partial<ServiceJobState>
  ) => {
    updateDatasetState(datasetName, state => ({
      ...state,
      jobStates: {
        ...state.jobStates,
        [serviceType]: { ...state.jobStates[serviceType], ...updates },
      },
    }));
  };

  const clearJobState = (datasetName: string, serviceType: ServiceType) => {
    updateJobState(datasetName, serviceType, {
      jobId: null,
      status: null,
      isPolling: false,
      error: null,
    });
  };

  const ensurePollIntervals = (datasetName: string) => {
    if (!pollIntervalRefs.current[datasetName]) {
      pollIntervalRefs.current[datasetName] = {
        FD_DISCOVERY: null,
        SUCCINCTNESS: null,
        COVERAGE: null,
        GENUINENESS: null,
        RELATIONAL_INFORMATION_CONTENT: null,
        EVALUATION_PATTERNS: null,
      };
    }
  };

  const clearPollingInterval = (datasetName: string, serviceType: ServiceType) => {
    const map = pollIntervalRefs.current[datasetName];
    if (map && map[serviceType]) {
      clearInterval(map[serviceType]!);
      map[serviceType] = null;
    }
  };

  // Generic calculation handler
  // Fetch datasets helper - can be called to refresh (force=true) or initial fetch
  const fetchDatasets = async (force: boolean = false) => {
    if (hasFetchedDatasets.current && !force) return;

    try {
      setIsLoadingDatasets(true);
      setDatasetsError(null);

      // Check if service is UP
      const health = await eurekaClient.isServiceUp(SERVICE_NAME);

      if (!health.isUp) {
        setDatasetsError('Service is not available');
        return;
      }

      // Call datasets endpoint
      const response = await apiClient.callServiceEndpoint(SERVICE_NAME, DATASETS_ENDPOINT, {
        method: 'GET',
      });

      // Handle different response formats
      let datasetList: Dataset[] = [];
      if (Array.isArray(response.data)) {
        if (response.data.length > 0 && typeof response.data[0] === 'string') {
          datasetList = response.data.map((name: string) => ({ name }));
        } else {
          datasetList = response.data;
        }
      } else if (response.data && typeof response.data === 'object') {
        if (Array.isArray(response.data.datasets)) {
          if (typeof response.data.datasets[0] === 'string') {
            datasetList = response.data.datasets.map((name: string) => ({ name }));
          } else {
            datasetList = response.data.datasets;
          }
        } else if (Array.isArray(response.data.data)) {
          if (typeof response.data.data[0] === 'string') {
            datasetList = response.data.data.map((name: string) => ({ name }));
          } else {
            datasetList = response.data.data;
          }
        } else {
          datasetList = Object.values(response.data) as Dataset[];
        }
      }

      setDatasets(datasetList);
      // Show explicit message when no datasets are returned
      if (datasetList.length === 0) {
        setDatasetsError('No datasets found');
      }
      hasFetchedDatasets.current = true;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to fetch datasets';
      setDatasetsError(errorMessage);
      console.error('Error fetching datasets:', error);
    } finally {
      setIsLoadingDatasets(false);
    }
  };

  // Fetch entropy datasets from RELATIONAL_INFORMATION_CONTENT service
  const fetchEntropyDatasets = async (force: boolean = false) => {
    if (hasFetchedDatasets.current && !force && entropyDatasets.length > 0) return;

    try {
      setIsLoadingEntropyDatasets(true);
      setEntropyDatasetsError(null);

      const health = await eurekaClient.isServiceUp(SERVICES.RELATIONAL_INFORMATION_CONTENT);
      if (!health.isUp) {
        setEntropyDatasetsError('Entropy service is not available');
        return;
      }

      const response = await apiClient.callServiceEndpoint(
        SERVICES.RELATIONAL_INFORMATION_CONTENT,
        '/dataset/list',
        { method: 'GET' }
      );

      let list: string[] = [];
      if (Array.isArray(response.data)) {
        list = response.data.map((x: any) => String(x));
      } else if (response.data && Array.isArray(response.data.datasets)) {
        list = response.data.datasets.map((x: any) => String(x));
      } else if (response.data && Array.isArray(response.data.data)) {
        list = response.data.data.map((x: any) => String(x));
      }

      setEntropyDatasets(list);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to fetch entropy datasets';
      setEntropyDatasetsError(errorMessage);
      console.error('Error fetching entropy datasets:', error);
    } finally {
      setIsLoadingEntropyDatasets(false);
    }
  };

  // Initial fetch once on mount
  useEffect(() => {
    fetchDatasets(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    fetchDatasets(false);
    fetchEntropyDatasets(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Handle file selection
  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    setUploadMessage(null);
    setUploadError(null);
    const file = e.target.files && e.target.files[0];
    if (!file) {
      setSelectedFile(null);
      return;
    }

    // accept only .csv
    if (!file.name.toLowerCase().endsWith('.csv')) {
      setSelectedFile(null);
      setUploadError('Only .csv files are accepted');
      return;
    }

    setSelectedFile(file);
  };

  // Upload handler
  const handleUpload = async () => {
    setUploadMessage(null);
    setUploadError(null);

    if (!selectedFile) {
      setUploadError('Please select a .csv file to upload');
      return;
    }

    try {
      setIsUploading(true);

      const formData = new FormData();
      // the backend expects the file parameter as 'file' (common convention)
      formData.append('file', selectedFile, selectedFile.name);

      // Upload to all services that need the dataset file
      const uploadPromises = [
        apiClient.callServiceEndpoint(SERVICE_NAME, '/dataset/upload', {
          method: 'POST',
          data: formData,
        }),
        apiClient.callServiceEndpoint(SERVICES.COVERAGE, '/dataset/upload', {
          method: 'POST',
          data: formData,
        }),
        apiClient.callServiceEndpoint(SERVICES.GENUINENESS, '/dataset/upload', {
          method: 'POST',
          data: formData,
        }),
        apiClient.callServiceEndpoint(SERVICES.EVALUATION_PATTERNS, '/dataset/upload', {
          method: 'POST',
          data: formData,
        }),
      ];

      const responses = await Promise.all(uploadPromises);

      // The service returns a simple OK string: "File uploaded successfully: <name>"
      const respText = typeof responses[0].data === 'string' ? responses[0].data : JSON.stringify(responses[0].data);
      setUploadMessage(respText);
      // Clear selection on success
      setSelectedFile(null);
  // Refresh dataset list automatically
  await fetchDatasets(true);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Upload failed';
      setUploadError(errorMessage);
      console.error('Upload error:', err);
    } finally {
      setIsUploading(false);
    }
  };

  const handleViewDetails = async (dataset: Dataset) => {
    const datasetName = dataset.name;
    const filename = dataset.name.endsWith('.csv') ? dataset.name.slice(0, -4) : dataset.name;

    setSelectedDatasets(prev => (prev.includes(datasetName) ? prev : [...prev, datasetName]));
    ensurePollIntervals(datasetName);

    setDatasetStates(prev => {
      const baseState: DatasetDetailState = prev[datasetName] ?? {
        extractRules: [],
        calculations: createInitialCalculations(),
        currentFilename: filename,
        rulesError: null,
        isLoadingRules: false,
        jobStates: createInitialJobStates(),
        currentPage: 1,
        showEntropyDialog: false,
        entropyOptions: createInitialEntropyOptions(),
      };

      return {
        ...prev,
        [datasetName]: {
          ...baseState,
          extractRules: [],
          calculations: createInitialCalculations(),
          rulesError: null,
          isLoadingRules: true,
          jobStates: createInitialJobStates(),
          currentPage: 1,
          currentFilename: filename,
        },
      };
    });

    try {
      const jobResponse = await apiClient.submitJob(SERVICE_NAME, '/jobs', {
        filename,
      });

      updateJobState(datasetName, 'FD_DISCOVERY', {
        jobId: jobResponse.jobId,
        status: jobResponse.status,
        isPolling: true,
        error: null,
      });

      await pollJobCompletion(datasetName, 'FD_DISCOVERY', jobResponse.jobId);
      fetchEntropyDatasets(true);

      setTimeout(() => {
        detailsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 100);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to submit FD discovery job';
      updateDatasetState(datasetName, state => ({ ...state, rulesError: errorMessage }));
      updateJobState(datasetName, 'FD_DISCOVERY', {
        error: errorMessage,
        isPolling: false,
      });
      console.error('Error submitting FD discovery job:', error);
    } finally {
      updateDatasetState(datasetName, state => ({ ...state, isLoadingRules: false }));
    }
  };

  // Generic polling function for any service
  const pollJobCompletion = async (datasetName: string, serviceType: ServiceType, jobId: string) => {
    ensurePollIntervals(datasetName);
    clearPollingInterval(datasetName, serviceType);

    let isCompleted = false;

    const poll = async () => {
      try {
        const serviceName = SERVICE_TYPE_MAP[serviceType];
        const jobStatus = await apiClient.pollJobStatus<any>(serviceName, jobId);

        updateJobState(datasetName, serviceType, {
          status: jobStatus.status,
        });

        if (jobStatus.status === 'FINISHED') {
          isCompleted = true;

          if (serviceType === 'FD_DISCOVERY' && jobStatus.result) {
            handleFDDiscoveryResult(datasetName, jobStatus.result);
          } else if (serviceType === 'SUCCINCTNESS' && jobStatus.result) {
            handleSuccinctnessResult(datasetName, jobStatus.result);
          } else if (serviceType === 'COVERAGE' && jobStatus.result) {
            handleCoverageResult(datasetName, jobStatus.result);
          } else if (serviceType === 'GENUINENESS' && jobStatus.result) {
            handleGenuinenessResult(datasetName, jobStatus.result);
          } else if (serviceType === 'RELATIONAL_INFORMATION_CONTENT' && jobStatus.result) {
            handleEntropyResult(datasetName, jobStatus.result);
          }

          updateJobState(datasetName, serviceType, {
            isPolling: false,
          });

          clearPollingInterval(datasetName, serviceType);
        } else if (jobStatus.status === 'FAILED') {
          isCompleted = true;
          updateJobState(datasetName, serviceType, {
            isPolling: false,
            error: jobStatus.error || 'Job failed',
          });

          const calcType = serviceType.toLowerCase();
          if (calcType === 'fd_discovery') {
            updateDatasetState(datasetName, state => ({
              ...state,
              rulesError: jobStatus.error || 'FD discovery job failed',
            }));
          } else {
            updateCalculationState(datasetName, calcType, {
              error: jobStatus.error || `${serviceType} job failed`,
            });
          }

          clearPollingInterval(datasetName, serviceType);
        }
      } catch (error) {
        console.error(`Error polling ${serviceType} job status:`, error);
        updateJobState(datasetName, serviceType, {
          error: error instanceof Error ? error.message : 'Polling error',
        });
      }
    };

    await poll();

    if (!isCompleted) {
      pollIntervalRefs.current[datasetName][serviceType] = setInterval(poll, 2000);
    }
  };

  // Result handlers for each service type
  const handleFDDiscoveryResult = (datasetName: string, result: any) => {
    // Parse rules from result
    const parseRuleObject = (ruleObj: Record<string, unknown> | string): ExtractRule | null => {
      let namesStr = '';
      let indicesStr = '';
      
      if (typeof ruleObj === 'string') {
        namesStr = ruleObj;
      } else if (typeof ruleObj === 'object' && ruleObj !== null) {
        namesStr = String(ruleObj.names || '');
        indicesStr = String(ruleObj.indices || '');
      }
      
      if (!namesStr) return null;
      
      const parts = namesStr.split('->');
      if (parts.length !== 2) return null;
      
      let lhs = parts[0].trim();
      if (lhs.startsWith('[') && lhs.endsWith(']')) {
        lhs = lhs.slice(1, -1);
      }
      const lhsArr = lhs.split(',').map(s => s.trim()).filter(Boolean);
      const lhsDisplay = lhsArr.join(', ');
      const rhs = parts[1].trim();
      
      return { lhs: lhsDisplay, rhs, indicesStr };
    };

    let rules: ExtractRule[] = [];
    if (Array.isArray(result)) {
      rules = result
        .map((ruleItem: unknown) => parseRuleObject(ruleItem as Record<string, unknown> | string))
        .filter((rule): rule is ExtractRule => rule !== null);
    } else if (typeof result === 'object' && result !== null) {
      const dataArray = (result as Record<string, unknown>).data || 
                       (result as Record<string, unknown>).rules || 
                       Object.values(result);
      if (Array.isArray(dataArray)) {
        rules = dataArray
          .map((ruleItem: unknown) => parseRuleObject(ruleItem as Record<string, unknown> | string))
          .filter((rule): rule is ExtractRule => rule !== null);
      }
    }

    updateDatasetState(datasetName, state => ({
      ...state,
      extractRules: rules,
    }));
  };

  const handleSuccinctnessResult = (datasetName: string, result: any) => {
    // Backend now returns just an array of doubles (scores)
    let scores: number[] = [];
    if (Array.isArray(result)) {
      scores = result.map((x: unknown) => Number(x));
    } else if (result && Array.isArray(result.results)) {
      scores = result.results.map((x: unknown) => Number(x));
    }
    
    // Convert to CalculationResult format with scores only
    updateDatasetState(datasetName, state => {
      const normalized: CalculationResult[] = scores.map((score, index) => ({
        values: state.extractRules[index]?.lhs.split(',').map(s => s.trim()) || [],
        attributeId: state.extractRules[index]?.rhs || '',
        score: score
      }));

      return {
        ...state,
        calculations: {
          ...state.calculations,
          succinctness: { ...state.calculations.succinctness, results: normalized, isCalculating: false },
        },
      };
    });
  };

  const handleCoverageResult = (datasetName: string, result: any) => {
    // Backend now returns just an array of doubles (scores)
    let scores: number[] = [];
    if (Array.isArray(result)) {
      scores = result.map((x: unknown) => Number(x));
    } else if (result && Array.isArray(result.results)) {
      scores = result.results.map((x: unknown) => Number(x));
    }
    
    // Convert to CalculationResult format with scores only
    updateDatasetState(datasetName, state => {
      const normalized: CalculationResult[] = scores.map((score, index) => ({
        values: state.extractRules[index]?.lhs.split(',').map(s => s.trim()) || [],
        attributeId: state.extractRules[index]?.rhs || '',
        score: score
      }));

      return {
        ...state,
        calculations: {
          ...state.calculations,
          coverage: { ...state.calculations.coverage, results: normalized, isCalculating: false },
        },
      };
    });
  };

  const handleGenuinenessResult = (datasetName: string, result: any) => {
    // Backend now returns just an array of doubles (scores)
    let scores: number[] = [];
    if (Array.isArray(result)) {
      scores = result.map((x: unknown) => Number(x));
    } else if (result && Array.isArray(result.results)) {
      scores = result.results.map((x: unknown) => Number(x));
    }
    
    // Convert to CalculationResult format with scores only
    updateDatasetState(datasetName, state => {
      const normalized: CalculationResult[] = scores.map((score, index) => ({
        values: state.extractRules[index]?.lhs.split(',').map(s => s.trim()) || [],
        attributeId: state.extractRules[index]?.rhs || '',
        score: score
      }));

      return {
        ...state,
        calculations: {
          ...state.calculations,
          genuineness: { ...state.calculations.genuineness, results: normalized, isCalculating: false },
        },
      };
    });
  };

  const handleEntropyResult = (datasetName: string, result: any) => {
    // Backend returns an array of doubles (entropy scores)
    let scores: number[] = [];
    if (Array.isArray(result)) {
      scores = result.map((x: unknown) => Number(x));
    } else if (result && Array.isArray(result.results)) {
      scores = result.results.map((x: unknown) => Number(x));
    }
    
    // Convert to CalculationResult format with scores only
    updateDatasetState(datasetName, state => {
      const normalized: CalculationResult[] = scores.map((score, index) => ({
        values: state.extractRules[index]?.lhs.split(',').map(s => s.trim()) || [],
        attributeId: state.extractRules[index]?.rhs || '',
        score: score
      }));

      return {
        ...state,
        calculations: {
          ...state.calculations,
          relationalInformationContent: {
            ...state.calculations.relationalInformationContent,
            results: normalized,
            isCalculating: false,
          },
        },
      };
    });
  };

  // Cleanup polling intervals on unmount
  useEffect(() => {
    return () => {
      Object.values(pollIntervalRefs.current).forEach(map => {
        Object.values(map).forEach(interval => {
          if (interval) clearInterval(interval);
        });
      });
      Object.values(evalPollRefs.current).forEach(interval => {
        if (interval) clearInterval(interval);
      });
    };
  }, []);

  const isEntropyDatasetAvailable = (name: string | null): boolean => {
    if (!name) return false;
    const base = name.endsWith('.csv') ? name.slice(0, -4) : name;
    // Prefer locally stored uploaded table filename, fallback to service list
    if (tableFileNamesByDataset[base]) return true;
    return entropyDatasets.some(d => {
      const db = d.endsWith('.csv') ? d.slice(0, -4) : d;
      return db === base;
    });
  };

  const normalizeFileName = (name: string): string => {
    return name.toLowerCase().endsWith('.csv') ? name.slice(0, -4).toLowerCase() : name.toLowerCase();
  };

  const doesFileMatchDataset = (fileName: string | undefined, datasetName: string): boolean => {
    if (!fileName) return false;
    return normalizeFileName(fileName) === normalizeFileName(datasetName);
  };

  const hasMatchingTableFile = (datasetName: string): boolean => {
    const baseDataset = normalizeFileName(datasetName);
    // Check if already uploaded in this session
    if (tableFileNamesByDataset[baseDataset]) return true;
    // Check if exists in entropy datasets from service
    return entropyDatasets.some(file => {
      return normalizeFileName(file) === baseDataset;
    });
  };

  const handleEntropyFileChange = (datasetName: string, file: File | null) => {
    setEntropyFiles(prev => ({ ...prev, [datasetName]: file }));
    if (file) {
      const matches = doesFileMatchDataset(file.name, datasetName);
      setFileNameMismatch(prev => ({ ...prev, [datasetName]: !matches }));
    } else {
      setFileNameMismatch(prev => ({ ...prev, [datasetName]: false }));
    }
  };

  const handleEntropyUpload = async (datasetName: string) => {
    const file = entropyFiles[datasetName];
    if (!file) return;

    setIsUploadingEntropy(prev => ({ ...prev, [datasetName]: true }));
    try {
      const formData = new FormData();
      formData.append('file', file, file.name);

      await apiClient.callServiceEndpoint(
        SERVICES.RELATIONAL_INFORMATION_CONTENT,
        '/dataset/upload',
        { method: 'POST', data: formData }
      );

      // Clear selection and refresh entropy dataset list
      setEntropyFiles(prev => ({ ...prev, [datasetName]: null }));
      setFileNameMismatch(prev => ({ ...prev, [datasetName]: false }));
      // Save base file name for later entropy requests
      const baseDataset = datasetName.endsWith('.csv') ? datasetName.slice(0, -4) : datasetName;
      const baseFile = file.name.toLowerCase().endsWith('.csv') ? file.name.slice(0, -4) : file.name;
      setTableFileNamesByDataset(prev => ({ ...prev, [baseDataset]: baseFile }));
      await fetchEntropyDatasets(true);
    } catch (err) {
      console.error('Entropy upload error:', err);
    } finally {
      setIsUploadingEntropy(prev => ({ ...prev, [datasetName]: false }));
    }
  };

  // Calculate succinctness by calling SUCCINCTNESS-SERVICE via job queue
  const handleCalculateSuccinctness = async (datasetName: string) => {
    const state = datasetStates[datasetName];
    if (!state || !state.extractRules.length) {
      updateCalculationState(datasetName, 'succinctness', { error: 'No functional dependencies to calculate' });
      return;
    }

    clearJobState(datasetName, 'SUCCINCTNESS');
    updateCalculationState(datasetName, 'succinctness', { isCalculating: true, error: null });

    try {
      const jobResponse = await apiClient.submitJob(SERVICES.SUCCINCTNESS, '/jobs', {
        filename: state.currentFilename!,
      });

      updateJobState(datasetName, 'SUCCINCTNESS', {
        jobId: jobResponse.jobId,
        status: jobResponse.status,
        isPolling: true,
        error: null,
      });

      await pollJobCompletion(datasetName, 'SUCCINCTNESS', jobResponse.jobId);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Succinctness calculation failed';
      updateCalculationState(datasetName, 'succinctness', { error: errorMessage, isCalculating: false });
      updateJobState(datasetName, 'SUCCINCTNESS', { error: errorMessage, isPolling: false });
      console.error('Succinctness error:', err);
    }
  };

  const handleCalculateCoverage = async (datasetName: string) => {
    const state = datasetStates[datasetName];
    if (!state || !state.extractRules.length) {
      updateCalculationState(datasetName, 'coverage', { error: 'No functional dependencies to calculate' });
      return;
    }

    if (!state.currentFilename) {
      updateCalculationState(datasetName, 'coverage', { error: 'Filename is missing' });
      return;
    }

    clearJobState(datasetName, 'COVERAGE');
    updateCalculationState(datasetName, 'coverage', { isCalculating: true, error: null });

    try {
      const jobResponse = await apiClient.submitJob(SERVICES.COVERAGE, '/jobs', {
        filename: state.currentFilename,
      });

      updateJobState(datasetName, 'COVERAGE', {
        jobId: jobResponse.jobId,
        status: jobResponse.status,
        isPolling: true,
        error: null,
      });

      await pollJobCompletion(datasetName, 'COVERAGE', jobResponse.jobId);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Coverage calculation failed';
      updateCalculationState(datasetName, 'coverage', { error: errorMessage, isCalculating: false });
      updateJobState(datasetName, 'COVERAGE', { error: errorMessage, isPolling: false });
      console.error('Coverage error:', err);
    }
  };

  const handleCalculateGenuineness = async (datasetName: string) => {
    const state = datasetStates[datasetName];
    if (!state || !state.extractRules.length) {
      updateCalculationState(datasetName, 'genuineness', { error: 'No functional dependencies to calculate' });
      return;
    }

    if (!state.currentFilename) {
      updateCalculationState(datasetName, 'genuineness', { error: 'Filename is missing' });
      return;
    }

    clearJobState(datasetName, 'GENUINENESS');
    updateCalculationState(datasetName, 'genuineness', { isCalculating: true, error: null });

    try {
      const jobResponse = await apiClient.submitJob(SERVICES.GENUINENESS, '/jobs', {
        filename: state.currentFilename,
      });

      updateJobState(datasetName, 'GENUINENESS', {
        jobId: jobResponse.jobId,
        status: jobResponse.status,
        isPolling: true,
        error: null,
      });

      await pollJobCompletion(datasetName, 'GENUINENESS', jobResponse.jobId);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Genuineness calculation failed';
      updateCalculationState(datasetName, 'genuineness', { error: errorMessage, isCalculating: false });
      updateJobState(datasetName, 'GENUINENESS', { error: errorMessage, isPolling: false });
      console.error('Genuineness error:', err);
    }
  };

  const handleCalculateEntropy = async (datasetName: string) => {
    updateDatasetState(datasetName, state => ({ ...state, showEntropyDialog: true }));
    setTimeout(() => {
      entropyDialogRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  };

  const handleEntropyDialogClose = (datasetName: string) => {
    updateDatasetState(datasetName, state => ({ ...state, showEntropyDialog: false }));
    setEntropyMinimized(prev => {
      const next = new Set(prev);
      next.delete(datasetName);
      return next;
    });
  };

  const handleToggleEntropyMinimize = (datasetName: string) => {
    setEntropyMinimized(prev => {
      const next = new Set(prev);
      if (next.has(datasetName)) {
        next.delete(datasetName);
      } else {
        next.add(datasetName);
      }
      return next;
    });
  };

  const handleEntropyDialogApply = async (datasetName: string) => {
    const state = datasetStates[datasetName];
    if (!state || !state.extractRules.length) {
      updateCalculationState(datasetName, 'relationalInformationContent', { 
        error: 'No functional dependencies to calculate' 
      });
      return;
    }

    if (!state.currentFilename) {
      updateCalculationState(datasetName, 'relationalInformationContent', { 
        error: 'Filename is missing' 
      });
      return;
    }

    updateCalculationState(datasetName, 'relationalInformationContent', { 
      isCalculating: true, 
      error: null 
    });

    try {
      const entropyParams: Record<string, string | number> = {
        filename: state.currentFilename,
        identifyOnes: state.entropyOptions.identifyOnes ? 'true' : 'false',
        considerSubtables: state.entropyOptions.considerSubtables ? 'true' : 'false',
        randomizedApproach: state.entropyOptions.randomizedApproach.enabled ? 'true' : 'false',
        runs: state.entropyOptions.randomizedApproach.runs,
        closure: state.entropyOptions.closure ? 'true' : 'false',
        saveResult: state.entropyOptions.saveResult.enabled ? 'true' : 'false'
      };

      const result = await apiClient.submitJob(
        SERVICES.RELATIONAL_INFORMATION_CONTENT,
        '/jobs',
        entropyParams
      );

      if (!result.jobId) {
        throw new Error('No job ID received from entropy service');
      }

      updateJobState(datasetName, 'RELATIONAL_INFORMATION_CONTENT', {
        jobId: result.jobId,
        status: result.status || 'NEW',
        isPolling: true,
        error: null,
      });

      pollJobCompletion(datasetName, 'RELATIONAL_INFORMATION_CONTENT', result.jobId);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Entropy job submission failed';
      updateCalculationState(datasetName, 'relationalInformationContent', { error: errorMessage, isCalculating: false });
      console.error('Entropy job submission error:', err);
    }
  };

  // ── Evaluation Patterns helpers ─────────────────────────────────────────

  const updateEvalState = (datasetName: string, updates: Partial<EvalPatternsState>) => {
    setEvalPatternsStates(prev => ({
      ...prev,
      [datasetName]: { ...(prev[datasetName] ?? createInitialEvalPatternsState()), ...updates },
    }));
  };

  const stopEvalPolling = (datasetName: string) => {
    const interval = evalPollRefs.current[datasetName];
    if (interval) {
      clearInterval(interval);
      evalPollRefs.current[datasetName] = null;
    }
  };

  const pollEvaluationPatterns = (datasetName: string, jobId: string) => {
    stopEvalPolling(datasetName);

    const poll = async () => {
      try {
        const resp = await apiClient.pollJobStatus(SERVICES.EVALUATION_PATTERNS, jobId);

        if (resp.status === 'FINISHED') {
          stopEvalPolling(datasetName);
          updateEvalState(datasetName, {
            isRunning: false,
            resultCsv: resp.resultCsv ?? null,
            completedRhs: resp.processingState?.completedRhs ?? [],
            currentRhs: null,
            question: null,
          });
        } else if (resp.status === 'FAILED') {
          stopEvalPolling(datasetName);
          updateEvalState(datasetName, {
            isRunning: false,
            error: resp.error ?? 'Job failed',
            question: null,
          });
        } else {
          // RUNNING or NEW — update progress/question from processingState
          const ps = resp.processingState;
          updateEvalState(datasetName, {
            completedRhs: ps?.completedRhs ?? [],
            currentRhs: ps?.currentRhs ?? null,
            question: ps?.question ?? null,
          });
        }
      } catch (err) {
        console.error('Error polling evaluation patterns job:', err);
      }
    };

    poll();
    evalPollRefs.current[datasetName] = setInterval(poll, 2000);
  };

  const handleExtractRules = async (datasetName: string) => {
    const state = datasetStates[datasetName];
    if (!state?.currentFilename) return;

    updateEvalState(datasetName, {
      ...createInitialEvalPatternsState(),
      show: true,
      isRunning: true,
    });

    try {
      const jobResp = await apiClient.submitJob(SERVICES.EVALUATION_PATTERNS, '/jobs', {
        filename: state.currentFilename,
      });
      updateEvalState(datasetName, { jobId: jobResp.jobId });
      pollEvaluationPatterns(datasetName, jobResp.jobId);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Extract Rules job failed';
      updateEvalState(datasetName, { isRunning: false, error: msg });
    }
  };

  const handleEvalAnswer = async (datasetName: string, genuine: boolean) => {
    const evalState = evalPatternsStates[datasetName];
    if (!evalState?.jobId) return;
    // Optimistically clear the question so the dialog closes immediately
    updateEvalState(datasetName, { question: null });
    await apiClient.answerSuspiciousQuestion(SERVICES.EVALUATION_PATTERNS, evalState.jobId, genuine);
  };

  const handleBuildNegativeExamples = async (datasetName: string) => {
    const evalState = evalPatternsStates[datasetName];
    if (!evalState?.resultCsv) return;

    // datasetName keeps the ".csv" suffix from the dataset list; the DB Payload stores
    // the name WITHOUT extension (set via state.currentFilename in job submission).
    const filename = datasetStates[datasetName]?.currentFilename ?? datasetName;

    // Parse antichain CSV (format: RHS,FakeLhs with header row)
    // Each data row: rhs,"{col1,col2,...}"
    const lines = evalState.resultCsv.trim().split('\n').slice(1); // skip header
    const targets: { lhs: string[]; rhs: string }[] = [];
    for (const line of lines) {
      const firstComma = line.indexOf(',');
      if (firstComma < 0) continue;
      const rhs = line.substring(0, firstComma).trim();
      const lhsRaw = line.substring(firstComma + 1).trim().replace(/^"|"$/g, ''); // strip outer quotes
      const lhs = lhsRaw.replace(/^\{|\}$/g, '').split(',').map(s => s.trim()).filter(Boolean);
      if (rhs && lhs.length > 0) targets.push({ lhs, rhs });
    }

    if (targets.length === 0) return;

    updateEvalState(datasetName, {
      negativeExamplesLoading: true,
      negativeExamplesResult: null,
      negativeExamplesError: null,
    });

    try {
      const result = await apiClient.buildNegativeExamples(SERVICE_NAME, filename, targets);
      updateEvalState(datasetName, { negativeExamplesLoading: false, negativeExamplesResult: result });
    } catch (err) {
      updateEvalState(datasetName, {
        negativeExamplesLoading: false,
        negativeExamplesError: err instanceof Error ? err.message : 'Unknown error',
      });
    }
  };

  const downloadEvalPatternsCSV = (datasetName: string) => {
    const csv = evalPatternsStates[datasetName]?.resultCsv;
    if (!csv) return;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.setAttribute('href', URL.createObjectURL(blob));
    link.setAttribute('download', `${datasetName.replace('.csv', '')}_fake_lhs.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleCloseEvalPanel = (datasetName: string) => {
    stopEvalPolling(datasetName);
    updateEvalState(datasetName, { show: false });
  };

  const downloadResultsAsCSV = (datasetName: string) => {
    const state = datasetStates[datasetName];
    if (!state || state.extractRules.length === 0) return;

    const headers = ['LHS', 'RHS', 'Succinctness', 'Coverage', 'Genuineness'];
    const rows = state.extractRules.map((rule, index) => {
      const lhsFormatted = `"[${rule.lhs}]"`;
      const rhs = rule.rhs;
      const succinctness = state.calculations.succinctness.results[index]?.score?.toFixed(4) || '';
      const coverage = state.calculations.coverage.results[index]?.score?.toFixed(4) || '';
      const genuineness = state.calculations.genuineness.results[index]?.score?.toFixed(4) || '';
      return [lhsFormatted, rhs, succinctness, coverage, genuineness].join(',');
    });

    const csvContent = [headers.join(','), ...rows].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `${state.currentFilename || 'results'}_analysis.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const downloadEntropyResults = async (datasetName: string) => {
    const state = datasetStates[datasetName];
    const jobId = state?.jobStates.RELATIONAL_INFORMATION_CONTENT.jobId;
    if (!state || !jobId) return;

    try {
      const response = await apiClient.callServiceEndpoint(
        SERVICES.RELATIONAL_INFORMATION_CONTENT,
        `/jobs/${jobId}/download`,
        { method: 'GET', responseType: 'blob' }
      );

      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      const url = URL.createObjectURL(blob);
      link.setAttribute('href', url);
      link.setAttribute('download', `${state.currentFilename || 'entropy'}_entropy.csv`);
      link.style.visibility = 'hidden';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      console.error('Error downloading entropy results:', err);
    }
  };

  const handleToggleMinimize = (datasetName: string) => {
    setMinimizedDatasets(prev => {
      const next = new Set(prev);
      if (next.has(datasetName)) {
        next.delete(datasetName);
      } else {
        next.add(datasetName);
      }
      return next;
    });
  };

  const handleCloseDetails = (datasetName: string) => {
    const intervals = pollIntervalRefs.current[datasetName];
    if (intervals) {
      Object.values(intervals).forEach(interval => {
        if (interval) clearInterval(interval);
      });
      delete pollIntervalRefs.current[datasetName];
    }
    stopEvalPolling(datasetName);

    setSelectedDatasets(prev => prev.filter(name => name !== datasetName));
    setMinimizedDatasets(prev => {
      const next = new Set(prev);
      next.delete(datasetName);
      return next;
    });
    setDatasetStates(prev => {
      const copy = { ...prev };
      delete copy[datasetName];
      return copy;
    });
  };

  return (
    <div className="space-y-6">
      {/* CSV Upload */}
      <Card className="border-l-4 border-l-blue-500">
        <CardHeader className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-950 dark:to-indigo-950">
          <CardTitle className="flex items-center gap-2">📤 Upload Main Dataset</CardTitle>
          <CardDescription>Select a CSV file to analyze for functional dependencies</CardDescription>
        </CardHeader>
        <CardContent className="pt-6">
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
            <label className="flex-1 px-4 py-3 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg cursor-pointer hover:border-blue-500 hover:bg-blue-50 dark:hover:bg-blue-950 transition-all">
              <input
                id="csv-upload"
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                className="hidden"
              />
              <div className="text-center">
                <div className="text-sm font-medium text-gray-700 dark:text-gray-300">
                  {selectedFile ? selectedFile.name : 'Click to select file'}
                </div>
                {!selectedFile && <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">or drag and drop</div>}
              </div>
            </label>
            <Button 
              onClick={handleUpload} 
              disabled={!selectedFile || isUploading}
              className="bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white shadow-md hover:shadow-lg transition-all px-6"
            >
              {isUploading ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Uploading...
                </>
              ) : (
                <>✓ Upload</>
              )}
            </Button>
          </div>
          {uploadMessage && (
            <div className="mt-4 p-3 bg-green-50 dark:bg-green-950 border border-green-200 dark:border-green-800 rounded-lg">
              <p className="text-sm text-green-700 dark:text-green-300 font-medium">✓ {uploadMessage}</p>
            </div>
          )}
          {uploadError && (
            <div className="mt-4 p-3 bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 rounded-lg">
              <p className="text-sm text-red-700 dark:text-red-300 font-medium">✕ {uploadError}</p>
            </div>
          )}
        </CardContent>
      </Card>
      {/* Datasets Table */}
      <Card>
        <CardHeader className="bg-gradient-to-r from-slate-50 to-gray-50 dark:from-slate-950 dark:to-gray-950">
          <CardTitle className="flex items-center gap-2">📊 Datasets</CardTitle>
          <CardDescription>
            List of available datasets from {SERVICE_NAME}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoadingDatasets ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin mr-2" />
              <span>Loading datasets...</span>
            </div>
          ) : datasetsError ? (
            <div className="p-3 bg-destructive/10 border border-destructive/20 rounded-md">
              <p className="text-sm text-destructive">{datasetsError}</p>
            </div>
          ) : datasets.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              No datasets found
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Dataset Name</TableHead>
                    <TableHead>Table File</TableHead>
                    <TableHead className="text-right"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {datasets
                    .slice((datasetsCurrentPage - 1) * datasetsPerPage, datasetsCurrentPage * datasetsPerPage)
                    .map((dataset, index) => {
                      const actualIndex = (datasetsCurrentPage - 1) * datasetsPerPage + index;
                      return (
                        <TableRow key={dataset.name || actualIndex}>
                          <TableCell className="font-medium">
                            {dataset.name || `Dataset ${actualIndex + 1}`}
                          </TableCell>
                    <TableCell>
                      <div className="space-y-2 w-full">
                        <label className="flex items-center gap-2 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg hover:border-amber-400 hover:bg-amber-50 dark:hover:bg-amber-950 transition-all cursor-pointer group">
                          <input
                            type="file"
                            accept=".csv"
                            onChange={e => handleEntropyFileChange(dataset.name, (e.target.files && e.target.files[0]) || null)}
                            className="hidden"
                          />
                          <span className="text-sm font-medium text-gray-700 dark:text-gray-300 group-hover:text-amber-700 dark:group-hover:text-amber-300 transition-colors">
                            {entropyFiles[dataset.name]?.name || '📁 Select file'}
                          </span>
                        </label>
                        <Button
                          onClick={() => handleEntropyUpload(dataset.name)}
                          disabled={
                            !entropyFiles[dataset.name] ||
                            isUploadingEntropy[dataset.name] ||
                            isLoadingEntropyDatasets ||
                            entropyDatasetsError !== null ||
                            fileNameMismatch[dataset.name]
                          }
                          className="w-full bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700 text-white shadow-md hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                          size="sm"
                        >
                          {isUploadingEntropy[dataset.name] ? (
                            <>
                              <Loader2 className="h-3 w-3 mr-2 animate-spin" />
                              Uploading...
                            </>
                          ) : (
                            <>📤 Upload Table</>  
                          )}
                        </Button>
                        {fileNameMismatch[dataset.name] && (
                          <div className="text-xs font-medium text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950 px-2 py-1 rounded">
                            ⚠ Must match: {normalizeFileName(dataset.name)}
                          </div>
                        )}
                        {hasMatchingTableFile(dataset.name) && (
                          <div className="text-xs font-bold text-green-700 dark:text-green-300 bg-green-100 dark:bg-green-950 px-2 py-1 rounded inline-block">
                            ✓ {normalizeFileName(dataset.name)}
                          </div>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        onClick={() => handleViewDetails(dataset)}
                        className="bg-gradient-to-r from-slate-600 to-slate-700 hover:from-slate-700 hover:to-slate-800 text-white shadow-md hover:shadow-lg transition-all"
                        size="sm"
                      >
                        <Eye className="h-4 w-4 mr-2" />
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
              </TableBody>
            </Table>
            
            {/* Datasets Pagination */}
            {Math.ceil(datasets.length / datasetsPerPage) > 1 && (
              <div className="flex items-center justify-between mt-4 pt-4 border-t">
                <div className="text-sm text-muted-foreground">
                  Showing {((datasetsCurrentPage - 1) * datasetsPerPage) + 1} to {Math.min(datasetsCurrentPage * datasetsPerPage, datasets.length)} of {datasets.length} datasets
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setDatasetsCurrentPage(p => Math.max(1, p - 1))}
                    disabled={datasetsCurrentPage === 1}
                  >
                    Previous
                  </Button>
                  <div className="flex items-center gap-1">
                    {Array.from({ length: Math.ceil(datasets.length / datasetsPerPage) }, (_, i) => i + 1).map(page => (
                      <Button
                        key={page}
                        variant={page === datasetsCurrentPage ? "default" : "outline"}
                        size="sm"
                        onClick={() => setDatasetsCurrentPage(page)}
                        className="w-8 h-8 p-0"
                      >
                        {page}
                      </Button>
                    ))}
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setDatasetsCurrentPage(p => Math.min(Math.ceil(datasets.length / datasetsPerPage), p + 1))}
                    disabled={datasetsCurrentPage === Math.ceil(datasets.length / datasetsPerPage)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </>
          )}
        </CardContent>
      </Card>

      {/* Dataset Details */}
      {selectedDatasets.map(datasetName => {
        const state = datasetStates[datasetName];
        if (!state) return null;
        const { extractRules, calculations, jobStates, rulesError, isLoadingRules, currentPage } = state;
        const startIndex = (currentPage - 1) * itemsPerPage;
        const endIndex = startIndex + itemsPerPage;
        const paginatedRules = extractRules.slice(startIndex, endIndex);
        const totalPages = Math.ceil(Math.max(extractRules.length, 1) / itemsPerPage);

        const isMinimized = minimizedDatasets.has(datasetName);

        return (
          <Card ref={detailsRef} key={datasetName} className="transition-all duration-300 ease-in-out">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>{datasetName} — Metrics calculation</CardTitle>
                <div className="flex items-center gap-2">
                  <Button
                    onClick={() => handleToggleMinimize(datasetName)}
                    variant="ghost"
                    size="sm"
                    className="hover:bg-gray-200 dark:hover:bg-gray-700 text-xl font-bold w-8 h-8 p-0 flex items-center justify-center transition-transform duration-200 hover:scale-110"
                  >
                    {isMinimized ? '+' : '−'}
                  </Button>
                  <Button
                    onClick={() => handleCloseDetails(datasetName)}
                    variant="ghost"
                    size="sm"
                    className="hover:bg-gray-200 dark:hover:bg-gray-700 text-lg w-8 h-8 p-0 flex items-center justify-center transition-transform duration-200 hover:scale-110"
                  >
                    ✕
                  </Button>
                </div>
              </div>
              {!isMinimized && (
                <div className="flex flex-wrap gap-2 transition-all duration-300 ease-in-out">
                  <Button
                    onClick={() => handleCalculateSuccinctness(datasetName)}
                    disabled={calculations.succinctness.isCalculating || extractRules.length === 0 || !serviceAvailability[SERVICES.SUCCINCTNESS]}
                    className="bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-700 hover:to-indigo-800 text-white shadow-md hover:shadow-lg transition-all disabled:opacity-50"
                    size="sm"
                  >
                    <Calculator className="h-4 w-4 mr-2" />
                    {calculations.succinctness.isCalculating ? 'Calculating...' : 'Succinctness'}
                  </Button>

                  <Button
                    onClick={() => handleCalculateCoverage(datasetName)}
                    disabled={calculations.coverage.isCalculating || extractRules.length === 0 || !state.currentFilename || !serviceAvailability[SERVICES.COVERAGE]}
                    className="bg-gradient-to-r from-emerald-600 to-emerald-700 hover:from-emerald-700 hover:to-emerald-800 text-white shadow-md hover:shadow-lg transition-all disabled:opacity-50"
                    size="sm"
                  >
                    <BarChart2 className="h-4 w-4 mr-2" />
                    {calculations.coverage.isCalculating ? 'Calculating...' : 'Coverage'}
                  </Button>

                  <Button
                    onClick={() => handleCalculateGenuineness(datasetName)}
                    disabled={calculations.genuineness.isCalculating || extractRules.length === 0 || !state.currentFilename || !serviceAvailability[SERVICES.GENUINENESS]}
                    className="bg-gradient-to-r from-purple-600 to-purple-700 hover:from-purple-700 hover:to-purple-800 text-white shadow-md hover:shadow-lg transition-all disabled:opacity-50"
                    size="sm"
                  >
                    <Target className="h-4 w-4 mr-2" />
                    {calculations.genuineness.isCalculating ? 'Calculating...' : 'Genuineness'}
                  </Button>

                  <Button
                    onClick={() => handleCalculateEntropy(datasetName)}
                    disabled={
                      calculations.relationalInformationContent.isCalculating ||
                      extractRules.length === 0 ||
                      !state.currentFilename ||
                      !isEntropyDatasetAvailable(state.currentFilename) ||
                      !serviceAvailability[SERVICES.RELATIONAL_INFORMATION_CONTENT]
                    }
                    className="bg-gradient-to-r from-cyan-600 to-cyan-700 hover:from-cyan-700 hover:to-cyan-800 text-white shadow-md hover:shadow-lg transition-all disabled:opacity-50"
                    size="sm"
                  >
                    <Info className="h-4 w-4 mr-2" />
                    {calculations.relationalInformationContent.isCalculating ? 'Calculating...' : 'Entropy'}
                  </Button>

                  <Button
                    onClick={() => handleExtractRules(datasetName)}
                    disabled={
                      extractRules.length === 0 ||
                      !state.currentFilename ||
                      !serviceAvailability[SERVICES.EVALUATION_PATTERNS] ||
                      (evalPatternsStates[datasetName]?.isRunning ?? false)
                    }
                    className="bg-gradient-to-r from-orange-500 to-orange-600 hover:from-orange-600 hover:to-orange-700 text-white shadow-md hover:shadow-lg transition-all disabled:opacity-50"
                    size="sm"
                  >
                    <Network className="h-4 w-4 mr-2" />
                    {(evalPatternsStates[datasetName]?.isRunning ?? false) ? 'Running...' : 'Extract Rules'}
                  </Button>
                </div>
              )}
            </CardHeader>
            {!isMinimized && (
            <CardContent className="space-y-6 max-h-96 overflow-y-auto transition-all duration-300 ease-in-out animate-in fade-in slide-in-from-top-2">
              {jobStates.FD_DISCOVERY.jobId && (
                <div className={`p-4 rounded-lg border-2 ${
                  jobStates.FD_DISCOVERY.status === 'FINISHED'
                    ? 'bg-green-50 dark:bg-green-950 border-green-300 dark:border-green-700'
                    : jobStates.FD_DISCOVERY.status === 'FAILED'
                    ? 'bg-red-50 dark:bg-red-950 border-red-300 dark:border-red-700'
                    : 'bg-blue-50 dark:bg-blue-950 border-blue-300 dark:border-blue-700'
                }`}>
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      {jobStates.FD_DISCOVERY.status === 'RUNNING' || jobStates.FD_DISCOVERY.status === 'NEW' ? (
                        <Loader2 className="h-5 w-5 animate-spin text-blue-600 dark:text-blue-400" />
                      ) : jobStates.FD_DISCOVERY.status === 'FINISHED' ? (
                        <div className="h-5 w-5 bg-green-600 dark:bg-green-400 rounded-full flex items-center justify-center text-white text-sm">✓</div>
                      ) : (
                        <div className="h-5 w-5 bg-red-600 dark:bg-red-400 rounded-full flex items-center justify-center text-white text-sm">✕</div>
                      )}
                      <span className="font-semibold">
                        FD Discovery: {' '}
                        {jobStates.FD_DISCOVERY.status === 'NEW' && 'Job Submitted'}
                        {jobStates.FD_DISCOVERY.status === 'RUNNING' && 'Processing...'}
                        {jobStates.FD_DISCOVERY.status === 'FINISHED' && 'Completed'}
                        {jobStates.FD_DISCOVERY.status === 'FAILED' && 'Failed'}
                      </span>
                    </div>
                    {jobStates.FD_DISCOVERY.error && (
                      <div className="text-sm text-red-600 dark:text-red-400">
                        Error: {jobStates.FD_DISCOVERY.error}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {(jobStates.SUCCINCTNESS.jobId || jobStates.COVERAGE.jobId || jobStates.GENUINENESS.jobId) && (
                <div className="space-y-3">
                  <h4 className="font-semibold text-sm">Calculation Jobs:</h4>

                  {jobStates.SUCCINCTNESS.jobId && (
                    <div className={`p-3 rounded-lg border ${
                      jobStates.SUCCINCTNESS.status === 'FINISHED'
                        ? 'bg-green-50 dark:bg-green-950 border-green-300'
                        : jobStates.SUCCINCTNESS.status === 'FAILED'
                        ? 'bg-red-50 dark:bg-red-950 border-red-300'
                        : 'bg-blue-50 dark:bg-blue-950 border-blue-300'
                    }`}>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          {jobStates.SUCCINCTNESS.isPolling ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : jobStates.SUCCINCTNESS.status === 'FINISHED' ? (
                            <span className="text-green-600">✓</span>
                          ) : (
                            <span className="text-red-600">✕</span>
                          )}
                          <span className="text-sm font-medium">Succinctness</span>
                        </div>
                        <span className="text-xs font-mono">{jobStates.SUCCINCTNESS.status}</span>
                      </div>
                    </div>
                  )}

                  {jobStates.COVERAGE.jobId && (
                    <div className={`p-3 rounded-lg border ${
                      jobStates.COVERAGE.status === 'FINISHED'
                        ? 'bg-green-50 dark:bg-green-950 border-green-300'
                        : jobStates.COVERAGE.status === 'FAILED'
                        ? 'bg-red-50 dark:bg-red-950 border-red-300'
                        : 'bg-blue-50 dark:bg-blue-950 border-blue-300'
                    }`}>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          {jobStates.COVERAGE.isPolling ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : jobStates.COVERAGE.status === 'FINISHED' ? (
                            <span className="text-green-600">✓</span>
                          ) : (
                            <span className="text-red-600">✕</span>
                          )}
                          <span className="text-sm font-medium">Coverage</span>
                        </div>
                        <span className="text-xs font-mono">{jobStates.COVERAGE.status}</span>
                      </div>
                    </div>
                  )}

                  {jobStates.GENUINENESS.jobId && (
                    <div className={`p-3 rounded-lg border ${
                      jobStates.GENUINENESS.status === 'FINISHED'
                        ? 'bg-green-50 dark:bg-green-950 border-green-300'
                        : jobStates.GENUINENESS.status === 'FAILED'
                        ? 'bg-red-50 dark:bg-red-950 border-red-300'
                        : 'bg-blue-50 dark:bg-blue-950 border-blue-300'
                    }`}>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          {jobStates.GENUINENESS.isPolling ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : jobStates.GENUINENESS.status === 'FINISHED' ? (
                            <span className="text-green-600">✓</span>
                          ) : (
                            <span className="text-red-600">✕</span>
                          )}
                          <span className="text-sm font-medium">Genuineness</span>
                        </div>
                        <span className="text-xs font-mono">{jobStates.GENUINENESS.status}</span>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {isLoadingRules ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="h-6 w-6 animate-spin mr-2" />
                  <span>Loading functional dependencies...</span>
                </div>
              ) : rulesError ? (
                <div className="p-3 bg-destructive/10 border border-destructive/20 rounded-md">
                  <p className="text-sm text-destructive">{rulesError}</p>
                </div>
              ) : extractRules.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">
                  No functional dependencies found
                </div>
              ) : (
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-semibold">Functional Dependencies</h3>
                    <Button
                      onClick={() => downloadResultsAsCSV(datasetName)}
                      className="bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 text-white shadow-md hover:shadow-lg transition-all"
                      size="sm"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                      </svg>
                      Download analysis
                    </Button>
                  </div>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="text-left">LHS</TableHead>
                        <TableHead className="text-center">RHS</TableHead>
                        <TableHead className="text-center">Succinctness Score</TableHead>
                        <TableHead className="text-center">Coverage Score</TableHead>
                        <TableHead className="text-center">Genuineness Score</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {paginatedRules.map((rule, pageIndex) => {
                        const actualIndex = startIndex + pageIndex;
                        return (
                          <TableRow key={actualIndex}>
                            <TableCell className="font-mono text-sm text-left">{rule.lhs}</TableCell>
                            <TableCell className="font-mono text-sm text-center">{rule.rhs}</TableCell>
                            <TableCell className="font-mono text-sm text-center">
                              {calculations.succinctness.isCalculating ? (
                                <Loader2 className="h-4 w-4 animate-spin inline-block" />
                              ) : (
                                calculations.succinctness.results[actualIndex]?.score !== undefined
                                  ? Number(calculations.succinctness.results[actualIndex].score).toFixed(3)
                                  : '—'
                              )}
                            </TableCell>
                            <TableCell className="font-mono text-sm text-center">
                              {calculations.coverage.isCalculating ? (
                                <Loader2 className="h-4 w-4 animate-spin inline-block" />
                              ) : (
                                calculations.coverage.results[actualIndex]?.score !== undefined
                                  ? Number(calculations.coverage.results[actualIndex].score).toFixed(3)
                                  : '—'
                              )}
                            </TableCell>
                            <TableCell className="font-mono text-sm text-center">
                              {calculations.genuineness.isCalculating ? (
                                <Loader2 className="h-4 w-4 animate-spin inline-block" />
                              ) : (
                                calculations.genuineness.results[actualIndex]?.score !== undefined
                                  ? Number(calculations.genuineness.results[actualIndex].score).toFixed(3)
                                  : '—'
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>

                  <div className="flex items-center justify-between mt-6 pt-4 border-t border-gray-200 dark:border-gray-700">
                    <div className="text-sm text-gray-600 dark:text-gray-400">
                      Showing {startIndex + 1} to {Math.min(endIndex, extractRules.length)} of {extractRules.length} rules
                    </div>
                    <div className="flex gap-2">
                      <Button
                        onClick={() => updateDatasetState(datasetName, s => ({ ...s, currentPage: Math.max(s.currentPage - 1, 1) }))}
                        disabled={currentPage === 1}
                        variant="outline"
                        size="sm"
                      >
                        ← Previous
                      </Button>
                      <div className="flex items-center gap-2">
                        {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                          <Button
                            key={page}
                            onClick={() => updateDatasetState(datasetName, s => ({ ...s, currentPage: page }))}
                            variant={currentPage === page ? 'default' : 'outline'}
                            size="sm"
                            className={currentPage === page ? 'bg-slate-600 hover:bg-slate-700' : ''}
                          >
                            {page}
                          </Button>
                        ))}
                      </div>
                      <Button
                        onClick={() => updateDatasetState(datasetName, s => ({ ...s, currentPage: Math.min(s.currentPage + 1, totalPages) }))}
                        disabled={currentPage === totalPages}
                        variant="outline"
                        size="sm"
                      >
                        Next →
                      </Button>
                    </div>
                  </div>
                  {calculations.succinctness.error && <div className="mt-4 text-sm text-destructive">{calculations.succinctness.error}</div>}
                  {calculations.coverage.error && <div className="mt-4 text-sm text-destructive">{calculations.coverage.error}</div>}
                  {calculations.genuineness.error && <div className="mt-4 text-sm text-destructive">{calculations.genuineness.error}</div>}
                </div>
              )}
            </CardContent>
            )}
          </Card>
        );
      })}

      {/* Entropy Options Dialogs per dataset */}
      {selectedDatasets.map(datasetName => {
        const state = datasetStates[datasetName];
        if (!state?.showEntropyDialog) return null;
        const isEntropyMinimized = entropyMinimized.has(datasetName);
        return (
          <Card ref={entropyDialogRef} className="border-2 border-cyan-500 shadow-lg transition-all duration-300 ease-in-out" key={`${datasetName}-entropy`}>
            <CardHeader className="bg-gradient-to-r from-cyan-50 to-blue-50 dark:from-cyan-950 dark:to-blue-950">
              <div className="flex items-center justify-between">
                <div className="flex flex-col">
                  <CardTitle className="flex items-center gap-2">{datasetName} — Entropy calculation</CardTitle>
                  {!isEntropyMinimized && (
                    <CardDescription>Configure optional parameters for entropy analysis</CardDescription>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    onClick={() => handleToggleEntropyMinimize(datasetName)}
                    variant="ghost"
                    size="sm"
                    className="hover:bg-gray-200 dark:hover:bg-gray-700 text-xl font-bold w-8 h-8 p-0 flex items-center justify-center transition-transform duration-200 hover:scale-110"
                  >
                    {isEntropyMinimized ? '+' : '−'}
                  </Button>
                  <Button
                    onClick={() => handleEntropyDialogClose(datasetName)}
                    variant="ghost"
                    size="sm"
                    className="hover:bg-gray-200 dark:hover:bg-gray-700 text-lg w-8 h-8 p-0 flex items-center justify-center transition-transform duration-200 hover:scale-110"
                  >
                    ✕
                  </Button>
                </div>
              </div>
            </CardHeader>
            {!isEntropyMinimized && (
            <CardContent className="pt-6 space-y-6 transition-all duration-300 ease-in-out animate-in fade-in slide-in-from-top-2">
              {state.jobStates.RELATIONAL_INFORMATION_CONTENT.jobId && (
                <div className={`p-4 rounded-lg border-2 ${
                  state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'FINISHED'
                    ? 'bg-green-50 dark:bg-green-950 border-green-300 dark:border-green-700'
                    : state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'FAILED'
                    ? 'bg-red-50 dark:bg-red-950 border-red-300 dark:border-red-700'
                    : 'bg-blue-50 dark:bg-blue-950 border-blue-300 dark:border-blue-700'
                }`}>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'RUNNING' || state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'NEW' ? (
                          <Loader2 className="h-5 w-5 animate-spin text-blue-600 dark:text-blue-400" />
                        ) : state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'FINISHED' ? (
                          <div className="h-5 w-5 bg-green-600 dark:bg-green-400 rounded-full flex items-center justify-center text-white text-sm">✓</div>
                        ) : (
                          <div className="h-5 w-5 bg-red-600 dark:bg-red-400 rounded-full flex items-center justify-center text-white text-sm">✕</div>
                        )}
                        <span className="font-semibold">
                          Entropy Calculation: {' '}
                          {state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'NEW' && 'Job Submitted'}
                          {state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'RUNNING' && 'Processing...'}
                          {state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'FINISHED' && 'Completed'}
                          {state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'FAILED' && 'Failed'}
                        </span>
                      </div>
                      {state.jobStates.RELATIONAL_INFORMATION_CONTENT.status === 'FINISHED' && (
                        <Button
                          onClick={() => downloadEntropyResults(datasetName)}
                          className="bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 text-white shadow-md hover:shadow-lg transition-all"
                          size="sm"
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                          </svg>
                          Download entropy
                        </Button>
                      )}
                    </div>
                    {state.jobStates.RELATIONAL_INFORMATION_CONTENT.error && (
                      <div className="text-sm text-red-600 dark:text-red-400">
                        Error: {state.jobStates.RELATIONAL_INFORMATION_CONTENT.error}
                      </div>
                    )}
                  </div>
                </div>
              )}

              <div className="flex items-center gap-4 p-4 border border-gray-200 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                <input
                  type="checkbox"
                  id={`entropy-identify-ones-${datasetName}`}
                  checked={state.entropyOptions.identifyOnes}
                  onChange={(e) =>
                    updateDatasetState(datasetName, s => ({
                      ...s,
                      entropyOptions: {
                        ...s.entropyOptions,
                        identifyOnes: e.target.checked
                      }
                    }))
                  }
                  className="w-5 h-5 cursor-pointer accent-cyan-600"
                />
                <label htmlFor={`entropy-identify-ones-${datasetName}`} className="cursor-pointer flex-1">
                  <div className="font-semibold text-gray-900 dark:text-gray-100">Identify Ones</div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">Enables a shortcut which identifies output cells containing a one and omits their calculations</div>
                </label>
              </div>

              <div className="flex items-center gap-4 p-4 border border-gray-200 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                <input
                  type="checkbox"
                  id={`entropy-subtables-${datasetName}`}
                  checked={state.entropyOptions.considerSubtables}
                  onChange={(e) =>
                    updateDatasetState(datasetName, s => ({
                      ...s,
                      entropyOptions: {
                        ...s.entropyOptions,
                        considerSubtables: e.target.checked
                      }
                    }))
                  }
                  className="w-5 h-5 cursor-pointer accent-cyan-600"
                />
                <label htmlFor={`entropy-subtables-${datasetName}`} className="cursor-pointer flex-1">
                  <div className="font-semibold text-gray-900 dark:text-gray-100">Consider Subtables</div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">Enables a shortcut which calculates entropies only for subtables while obtaining the same results as naive computation</div>
                </label>
              </div>

              <div className="p-4 border border-gray-200 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                <div className="flex items-start gap-4">
                  <input
                    type="checkbox"
                    id={`entropy-randomized-${datasetName}`}
                    checked={state.entropyOptions.randomizedApproach.enabled}
                    onChange={(e) =>
                      updateDatasetState(datasetName, s => ({
                        ...s,
                        entropyOptions: {
                          ...s.entropyOptions,
                          randomizedApproach: {
                            ...s.entropyOptions.randomizedApproach,
                            enabled: e.target.checked
                          }
                        }
                      }))
                    }
                    className="w-5 h-5 cursor-pointer accent-cyan-600 mt-1"
                  />
                  <div className="flex-1">
                    <label htmlFor={`entropy-randomized-${datasetName}`} className="cursor-pointer block">
                      <div className="font-semibold text-gray-900 dark:text-gray-100">Randomized Approach</div>
                      <div className="text-sm text-gray-600 dark:text-gray-400">Compute information content using an approximative algorithm by randomly enabling/disabling cells in the table for a fixed number of runs</div>
                    </label>
                    {state.entropyOptions.randomizedApproach.enabled && (
                      <div className="mt-3">
                        <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Number of Runs</label>
                        <input
                          type="number"
                          min="1"
                          value={state.entropyOptions.randomizedApproach.runs}
                          onChange={(e) =>
                            updateDatasetState(datasetName, s => ({
                              ...s,
                              entropyOptions: {
                                ...s.entropyOptions,
                                randomizedApproach: {
                                  ...s.entropyOptions.randomizedApproach,
                                  runs: parseInt(e.target.value) || 100000
                                }
                              }
                            }))
                          }
                          className="mt-1 w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
                          placeholder="100000"
                        />
                      </div>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-4 p-4 border border-gray-200 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                <input
                  type="checkbox"
                  id={`entropy-closure-${datasetName}`}
                  checked={state.entropyOptions.closure}
                  onChange={(e) =>
                    updateDatasetState(datasetName, s => ({
                      ...s,
                      entropyOptions: {
                        ...s.entropyOptions,
                        closure: e.target.checked
                      }
                    }))
                  }
                  className="w-5 h-5 cursor-pointer accent-cyan-600"
                />
                <label htmlFor={`entropy-closure-${datasetName}`} className="cursor-pointer flex-1">
                  <div className="font-semibold text-gray-900 dark:text-gray-100">Closure</div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">Execute computation using the transitive closure of the given functional dependencies</div>
                </label>
              </div>

              <div className="flex items-center gap-4 p-4 border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-100 dark:bg-gray-800">
                <input
                  type="checkbox"
                  id={`entropy-save-${datasetName}`}
                  checked={state.entropyOptions.saveResult.enabled}
                  disabled
                  className="w-5 h-5 cursor-not-allowed accent-cyan-600"
                />
                <label htmlFor={`entropy-save-${datasetName}`} className="cursor-not-allowed flex-1">
                  <div className="font-semibold text-gray-900 dark:text-gray-100">Save Result</div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">Save entropy analysis results to a CSV file</div>
                </label>
              </div>

              <div className="flex gap-3 pt-4">
                <Button
                  onClick={() => handleEntropyDialogApply(datasetName)}
                  className="flex-1 bg-gradient-to-r from-cyan-600 to-cyan-700 hover:from-cyan-700 hover:to-cyan-800 text-white shadow-md hover:shadow-lg transition-all"
                >
                  ✓ Apply & Calculate
                </Button>
                <Button
                  onClick={() => handleEntropyDialogClose(datasetName)}
                  variant="outline"
                  className="flex-1"
                >
                  Cancel
                </Button>
              </div>
            </CardContent>
            )}
          </Card>
        );
      })}

      {/* Evaluation Patterns processing panels — one per dataset */}
      {selectedDatasets.map(datasetName => {
        const evalState = evalPatternsStates[datasetName];
        if (!evalState?.show) return null;

        return (
          <Card
            key={`${datasetName}-eval`}
            className="border-2 border-orange-400 shadow-lg transition-all duration-300 ease-in-out"
          >
            <CardHeader className="bg-gradient-to-r from-orange-50 to-amber-50 dark:from-orange-950 dark:to-amber-950">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <Network className="h-5 w-5 text-orange-600" />
                    {datasetName} — Extract Rules
                  </CardTitle>
                  {!evalState.isRunning && !evalState.resultCsv && !evalState.error && (
                    <CardDescription>Lattice-based FD quality assessment</CardDescription>
                  )}
                </div>
                <Button
                  onClick={() => handleCloseEvalPanel(datasetName)}
                  variant="ghost"
                  size="sm"
                  className="hover:bg-gray-200 dark:hover:bg-gray-700 text-lg w-8 h-8 p-0 flex items-center justify-center"
                >
                  ✕
                </Button>
              </div>
            </CardHeader>

            <CardContent className="pt-6 space-y-4 transition-all duration-300 ease-in-out animate-in fade-in slide-in-from-top-2">

              {/* Error banner */}
              {evalState.error && (
                <div className="p-3 bg-red-50 dark:bg-red-950 border border-red-300 dark:border-red-700 rounded-lg text-sm text-red-700 dark:text-red-300">
                  Error: {evalState.error}
                </div>
              )}

              {/* Progress list */}
              {(evalState.isRunning || evalState.resultCsv || evalState.completedRhs.length > 0) && (
                <div className="space-y-2">
                  <p className="text-sm font-semibold text-gray-700 dark:text-gray-300">
                    RHS columns:
                  </p>

                  {/* Completed RHS rows — show antichain */}
                  {evalState.completedRhs.map(({ rhs, antichain }) => (
                    <div
                      key={rhs}
                      className="px-3 py-2 rounded-lg bg-green-50 dark:bg-green-950 border border-green-200 dark:border-green-800"
                    >
                      <div className="flex items-center gap-3">
                        <div className="h-5 w-5 bg-green-600 rounded-full flex items-center justify-center text-white text-xs shrink-0">
                          ✓
                        </div>
                        <span className="font-mono text-sm font-semibold text-green-800 dark:text-green-200">{rhs}</span>
                      </div>
                      {antichain.length > 0 ? (
                        <div className="mt-1 ml-8 flex flex-wrap gap-1">
                          {antichain.map(lhs => (
                            <span
                              key={lhs}
                              className="inline-block px-2 py-0.5 rounded bg-green-100 dark:bg-green-900 border border-green-300 dark:border-green-700 font-mono text-xs text-green-700 dark:text-green-300"
                            >
                              {lhs}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <p className="mt-1 ml-8 text-xs text-green-600 dark:text-green-400 italic">no fake LHS</p>
                      )}
                    </div>
                  ))}

                  {/* Currently running RHS row */}
                  {evalState.currentRhs && evalState.isRunning && (
                    <div className="flex items-center gap-3 px-3 py-2 rounded-lg bg-blue-50 dark:bg-blue-950 border border-blue-200 dark:border-blue-800">
                      <Loader2 className="h-5 w-5 animate-spin text-blue-600 dark:text-blue-400 shrink-0" />
                      <span className="font-mono text-sm text-blue-800 dark:text-blue-200">
                        {evalState.currentRhs}
                      </span>
                      <span className="text-xs text-blue-600 dark:text-blue-400">processing…</span>
                    </div>
                  )}

                  {/* All done indicator */}
                  {!evalState.isRunning && evalState.resultCsv && (
                    <div className="flex items-center gap-3 px-3 py-2 rounded-lg bg-green-50 dark:bg-green-950 border-2 border-green-400 dark:border-green-600">
                      <div className="h-5 w-5 bg-green-600 rounded-full flex items-center justify-center text-white text-xs shrink-0">
                        ✓
                      </div>
                      <span className="text-sm font-semibold text-green-800 dark:text-green-200">
                        Analysis complete
                      </span>
                    </div>
                  )}
                </div>
              )}

              {/* SUSPICIOUS question dialog */}
              {evalState.question && evalState.isRunning && (
                <div className="p-4 rounded-lg border-2 border-amber-400 bg-amber-50 dark:bg-amber-950 space-y-3">
                  <div className="flex items-start gap-2">
                    <span className="text-amber-600 dark:text-amber-400 text-lg shrink-0">?</span>
                    <div className="space-y-1">
                      <p className="font-semibold text-amber-900 dark:text-amber-100 text-sm">
                        User input needed
                      </p>
                      <p className="text-sm text-amber-800 dark:text-amber-200">
                        {evalState.question.text}
                      </p>
                      <p className="text-xs text-amber-600 dark:text-amber-400 font-mono">
                        {'{' + evalState.question.lhs.join(', ') + '}'} &rarr; {evalState.question.rhs}
                      </p>
                    </div>
                  </div>
                  <div className="flex gap-3">
                    <Button
                      onClick={() => handleEvalAnswer(datasetName, true)}
                      className="flex-1 bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 text-white shadow-md"
                      size="sm"
                    >
                      Yes, genuine
                    </Button>
                    <Button
                      onClick={() => handleEvalAnswer(datasetName, false)}
                      className="flex-1 bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 text-white shadow-md"
                      size="sm"
                    >
                      No, fake
                    </Button>
                  </div>
                </div>
              )}

              {/* Download button */}
              {evalState.resultCsv && (
                <div className="pt-2">
                  <Button
                    onClick={() => downloadEvalPatternsCSV(datasetName)}
                    className="bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 text-white shadow-md hover:shadow-lg transition-all"
                    size="sm"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                    Download fake LHS antichain
                  </Button>
                </div>
              )}

              {/* Negative Examples section */}
              {evalState.resultCsv && (
                <div className="pt-3 border-t border-gray-200 dark:border-gray-700 mt-3">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm font-semibold text-gray-700 dark:text-gray-300">
                      Negative Examples
                    </p>
                    <Button
                      size="sm"
                      onClick={() => handleBuildNegativeExamples(datasetName)}
                      disabled={evalState.negativeExamplesLoading}
                      className="bg-gradient-to-r from-yellow-500 to-yellow-600 hover:from-yellow-600 hover:to-yellow-700 text-white shadow-md hover:shadow-lg transition-all disabled:opacity-50"
                    >
                      {evalState.negativeExamplesLoading ? (
                        <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Building...</>
                      ) : (
                        <>
                          <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                          </svg>
                          Build Negative Examples
                        </>
                      )}
                    </Button>
                  </div>

                  {evalState.negativeExamplesError && (
                    <p className="text-sm text-red-600 dark:text-red-400">{evalState.negativeExamplesError}</p>
                  )}

                  {evalState.negativeExamplesResult && (
                    <div className="space-y-4 mt-2">
                      {evalState.negativeExamplesResult.negativeExamples.map((entry, i) => (
                        <div key={i} className="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
                          <div className="px-3 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
                            <span className="font-mono text-sm font-semibold text-orange-600 dark:text-orange-400">
                              {'{' + entry.lhs.join(', ') + '}'} → {entry.rhs}
                            </span>
                            <span className="ml-2 text-xs text-gray-500 dark:text-gray-400">(fake FD — negative example shown in yellow)</span>
                          </div>
                          <div className="overflow-x-auto">
                            <Table>
                              <TableHeader>
                                <TableRow>
                                  {evalState.negativeExamplesResult!.columnNames.map(col => (
                                    <TableHead
                                      key={col}
                                      className={
                                        entry.lhs.includes(col)
                                          ? 'bg-blue-50 dark:bg-blue-950 font-semibold'
                                          : col === entry.rhs
                                          ? 'bg-orange-50 dark:bg-orange-950 font-semibold'
                                          : ''
                                      }
                                    >
                                      {col}
                                    </TableHead>
                                  ))}
                                </TableRow>
                              </TableHeader>
                              <TableBody>
                                {entry.rows.map((row, ri) => (
                                  <TableRow
                                    key={ri}
                                    className={
                                      row.type === 'negative'
                                        ? 'bg-yellow-100 dark:bg-yellow-900/40'
                                        : row.type === 'base'
                                        ? 'bg-red-50 dark:bg-red-950/30'
                                        : ''
                                    }
                                  >
                                    {row.values.map((val, ci) => (
                                      <TableCell key={ci} className="text-xs font-mono">{val}</TableCell>
                                    ))}
                                  </TableRow>
                                ))}
                              </TableBody>
                            </Table>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
