import axios from 'axios';

/*
 * Where the backend lives.
 *
 * In development this is left unset: requests go to "/api" on the Vite dev server, which proxies
 * them to localhost:8082 (see vite.config.js). A deployed site has no dev server and no proxy,
 * so the build is given the backend's public address instead, e.g.
 *     VITE_API_URL=https://ngoconnect-backend.onrender.com
 * Vite bakes this in at build time, so changing it means rebuilding the frontend.
 */
const backendUrl = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');

const api = axios.create({
  baseURL: `${backendUrl}/api`,
  headers: { 'Content-Type': 'application/json' },
});

export default api;
