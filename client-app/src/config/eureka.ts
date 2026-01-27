/// <reference types="vite/client" />

// Eureka Server Configuration
const envEurekaUrl = import.meta.env.VITE_EUREKA_URL;

export const EUREKA_URL = envEurekaUrl && envEurekaUrl.trim().length > 0
  ? envEurekaUrl
  : import.meta.env.DEV
    ? '/eureka' // Use Vite proxy in development when no explicit URL is provided
    : 'http://localhost:8761'; // Default production fallback

