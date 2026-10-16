import { createContext, useContext } from 'react';

/** Shared state for everything rendered inside a project workspace. */
export const WorkspaceContext = createContext(null);
export const useWorkspace = () => useContext(WorkspaceContext);
