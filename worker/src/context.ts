import { Store } from "./store";
import { Env, FetchFn } from "./types";

export interface AppContext {
  request: Request;
  env: Env;
  store: Store;
  nowMs: number;
  nowSec: number;
  fetchFn: FetchFn;
  ip: string;
}
