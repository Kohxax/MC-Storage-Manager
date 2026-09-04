export interface Player { id: string; displayName: string; minecraftUuid: string; permissions: string[] }
export interface Region { id: string; ownerPlayerId: string; name: string; worldName: string; dimensionKey: string; status: 'active' | 'invalid' | 'deleted'; updatedAt: string; lastScanAt: string | null; revision: number }
export interface RegionItems { region: Region; containers: Array<{ items: Array<{ itemKey: string; amount: number }> }> }
export interface InventoryItem { itemKey: string; amount: number }
