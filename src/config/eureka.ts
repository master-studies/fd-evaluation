/// <reference types="vite/client" />

// Eureka Server Configuration
// In development, use the Vite proxy path to avoid CORS issues
// In production, use the full Eureka server URL

const eurekaUrl = import.meta.env.VITE_EUREKA_URL || 'http://localhost:8761';

export const EUREKA_URL = import.meta.env.DEV 
  ? '/eureka'  // Use Vite proxy in development
  : eurekaUrl; // Full URL in production (set via VITE_EUREKA_URL at build time)

