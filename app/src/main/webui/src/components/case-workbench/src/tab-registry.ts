export interface CaseTabDefinition {
  id: string;
  label: string;
  tagName: string;
  icon?: string;
  order: number;
  badge?: (caseData: object) => string | null;
}

const tabs: CaseTabDefinition[] = [];
const registeredIds = new Set<string>();

export function registerCaseTab(def: CaseTabDefinition): void {
  if (registeredIds.has(def.id)) {
    throw new Error(`Tab already registered: ${def.id}`);
  }
  registeredIds.add(def.id);
  tabs.push(def);
  tabs.sort((a, b) => a.order - b.order);
}

export function getCaseTabs(): readonly CaseTabDefinition[] {
  return tabs;
}
