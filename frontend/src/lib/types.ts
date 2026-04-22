// ---- Enums ----
export type TokenStatus = 'WAITING' | 'SERVING' | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED';
export type TokenPriority = 'NORMAL' | 'HIGH' | 'VIP';
export type TokenSource = 'WALK_IN' | 'APPOINTMENT' | 'KIOSK' | 'ONLINE';
export type CounterStatus = 'OPEN' | 'CLOSED' | 'ON_BREAK';
export type AppointmentStatus = 'BOOKED' | 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type UserRole = 'SUPER_ADMIN' | 'BRANCH_ADMIN' | 'STAFF' | 'RECEPTIONIST';

// ---- Models ----
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

export interface TokenResponse {
  id: number;
  tokenNumber: string;
  branchId: number;
  branchName: string;
  serviceId: number;
  serviceName: string;
  counterId?: number;
  counterName?: string;
  customerName?: string;
  customerPhone?: string;
  customerEmail?: string;
  status: TokenStatus;
  priority: TokenPriority;
  source: TokenSource;
  positionInQueue?: number;
  issuedAt: string;
  calledAt?: string;
  servingStartedAt?: string;
  completedAt?: string;
  estimatedWaitMinutes?: number;
  notes?: string;
}

export interface CounterStatusResponse {
  counterId: number;
  counterNumber: number;
  counterName: string;
  status: CounterStatus;
  currentToken?: TokenResponse;
  serviceNames: string[];
}

export interface QueueStatusResponse {
  branchId: number;
  branchName: string;
  totalWaiting: number;
  totalServing: number;
  totalCompleted: number;
  averageWaitMinutes: number;
  waitingTokens: TokenResponse[];
  counters: CounterStatusResponse[];
}

export interface BranchResponse {
  id: number;
  name: string;
  code: string;
  address?: string;
  phone?: string;
  timezone?: string;
  isActive: boolean;
}

export interface ServiceResponse {
  id: number;
  branchId: number;
  name: string;
  code: string;
  description?: string;
  avgServiceTimeMinutes: number;
  isActive: boolean;
}

export interface AppointmentResponse {
  id: number;
  branchId: number;
  branchName: string;
  serviceId: number;
  serviceName: string;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  queueTokenId?: number;
  queueTokenNumber?: string;
  notes?: string;
  createdAt: string;
}

export interface SlotResponse {
  startTime: string;
  endTime: string;
  available: boolean;
}

export interface DashboardResponse {
  branchId: number;
  branchName: string;
  totalTokensToday: number;
  currentWaiting: number;
  currentServing: number;
  completedToday: number;
  noShowToday: number;
  cancelledToday: number;
  averageWaitMinutes: number;
  averageServiceMinutes: number;
  activeCounters: number;
  totalCounters: number;
  appointmentsToday: number;
  appointmentsCheckedIn: number;
  lastUpdated: string;
}

export interface AuthUser {
  id: number;
  email: string;
  firstName: string;
  lastName?: string;
  role: UserRole;
  branchId?: number;
  branchName?: string;
}

export interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
}
