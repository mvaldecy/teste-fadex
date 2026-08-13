export type ApiErrorCode =
  | "VALIDATION_ERROR"
  | "INVALID_PARAMETER"
  | "INVALID_BODY"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "INTERNAL_ERROR";

export type ApiFieldError = {
  field: string;
  message: string;
};

export type ApiErrorResponse = {
  code: ApiErrorCode;
  message: string;
  status: number;
  path: string;
  timestamp: string;
  fields?: ApiFieldError[];
};
