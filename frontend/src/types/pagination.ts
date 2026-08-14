export type SortDirection = "asc" | "desc";

export type PageParams = {
  page?: number;
  size?: number;
  sort?: string;
};

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  first: boolean;
  numberOfElements: number;
  empty: boolean;
};
