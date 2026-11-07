import { createContext, useContext, useState, ReactNode } from 'react'
import type { MerchantRestaurantDTO } from '../../api/merchant'

interface BranchContextValue {
  selectedBranch: MerchantRestaurantDTO | null
  setSelectedBranch: (branch: MerchantRestaurantDTO | null) => void
}

const BranchContext = createContext<BranchContextValue>({
  selectedBranch: null,
  setSelectedBranch: () => {},
})

export function BranchProvider({ children }: { children: ReactNode }) {
  const [selectedBranch, setSelectedBranch] = useState<MerchantRestaurantDTO | null>(() => {
    // Giữ lại chi nhánh đã chọn qua localStorage
    try {
      const saved = localStorage.getItem('selectedBranch')
      return saved ? JSON.parse(saved) : null
    } catch {
      return null
    }
  })

  const handleSet = (branch: MerchantRestaurantDTO | null) => {
    setSelectedBranch(branch)
    if (branch) localStorage.setItem('selectedBranch', JSON.stringify(branch))
    else localStorage.removeItem('selectedBranch')
  }

  return (
    <BranchContext.Provider value={{ selectedBranch, setSelectedBranch: handleSet }}>
      {children}
    </BranchContext.Provider>
  )
}

export function useBranch() {
  return useContext(BranchContext)
}
