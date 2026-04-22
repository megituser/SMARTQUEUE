import apiClient from './apiClient';

// ---- Auth ----
export const authApi = {
  login: (email: string, password: string) =>
    apiClient.post('/v1/auth/login', { email, password }),
  register: (data: any) =>
    apiClient.post('/v1/auth/register', data),
  refresh: (refreshToken: string) =>
    apiClient.post('/v1/auth/refresh', { refreshToken }),
  logout: (refreshToken: string) =>
    apiClient.post('/v1/auth/logout', { refreshToken }),
};

// ---- Branches ----
export const branchApi = {
  getAll: () => apiClient.get('/v1/branches'),
  getById: (id: number) => apiClient.get(`/v1/branches/${id}`),
  create: (data: any) => apiClient.post('/v1/branches', data),
  update: (id: number, data: any) => apiClient.put(`/v1/branches/${id}`, data),
  getServices: (branchId: number) => apiClient.get(`/v1/branches/${branchId}/services`),
  createService: (branchId: number, data: any) => apiClient.post(`/v1/branches/${branchId}/services`, data),
  getDashboard: (branchId: number) => apiClient.get(`/v1/branches/${branchId}/dashboard`),
};

// ---- Queue ----
export const queueApi = {
  issueToken: (data: any) => apiClient.post('/v1/queue/tokens', data),
  getToken: (id: number) => apiClient.get(`/v1/queue/tokens/${id}`),
  getStatus: (branchId: number) => apiClient.get(`/v1/queue/branch/${branchId}/status`),
  cancelToken: (id: number) => apiClient.post(`/v1/queue/tokens/${id}/cancel`),
};

// ---- Counters ----
export const counterApi = {
  getByBranch: (branchId: number) => apiClient.get(`/v1/counters/branch/${branchId}`),
  create: (data: any) => apiClient.post('/v1/counters', data),
  open: (id: number) => apiClient.post(`/v1/counters/${id}/open`),
  close: (id: number) => apiClient.post(`/v1/counters/${id}/close`),
  callNext: (id: number) => apiClient.post(`/v1/counters/${id}/next`),
  complete: (id: number) => apiClient.post(`/v1/counters/${id}/complete`),
  noShow: (id: number) => apiClient.post(`/v1/counters/${id}/no-show`),
};

// ---- Appointments ----
export const appointmentApi = {
  book: (data: any) => apiClient.post('/v1/appointments', data),
  getSlots: (branchId: number, serviceId: number, date: string) =>
    apiClient.get(`/v1/appointments/slots?branchId=${branchId}&serviceId=${serviceId}&date=${date}`),
  checkIn: (id: number) => apiClient.post(`/v1/appointments/${id}/check-in`),
  cancel: (id: number) => apiClient.post(`/v1/appointments/${id}/cancel`),
  getById: (id: number) => apiClient.get(`/v1/appointments/${id}`),
  getByBranch: (branchId: number, date?: string) =>
    apiClient.get(`/v1/appointments/branch/${branchId}${date ? `?date=${date}` : ''}`),
};

export default apiClient;
