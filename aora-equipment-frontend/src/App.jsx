import { createBrowserRouter, createRoutesFromElements, RouterProvider, Route, Navigate } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { useEffect, useRef } from 'react'

import RentalBillPage from './pages/billing/RentalBillPage'
import Login from './pages/auth/login'
import DashboardPage from './pages/dashboard/DashboardPage'
import EquipmentPage from './pages/equipment/EquipmentPage'
import TransferEntryPage from './pages/transfer/TransferEntryPage'
import TransferPage from './pages/transfer/TransferPage'
import EquipmentHistoryPage from './pages/equipment/EquipmentHistoryPage'
import CreateUserPage from './pages/users/CreateUserPage'
import CreateWorksitePage from './pages/worksites/CreateWorksitePage'
import DirectoryPage from './pages/directory/DirectoryPage'

function AuthCallback() {
  const { error, isLoading, isAuthenticated } = useAuth0()

  if (!isLoading && isAuthenticated && !error) return <Navigate to="/dashboard" replace />

  return (
    <div className="flex min-h-screen items-center justify-center">
      {isLoading && !error ? 'Completing sign in...' : null}
      {error ? `Authentication failed: ${error.message}` : null}
    </div>
  )
}

function ProtectedRoute({ children }) {
  const { error, isAuthenticated, isLoading, loginWithRedirect } = useAuth0()
  const redirectStarted = useRef(false)

  useEffect(() => {
    if (isLoading || error || isAuthenticated || redirectStarted.current) {
      return
    }

    redirectStarted.current = true
    loginWithRedirect({
      appState: {
        returnTo: window.location.pathname,
      },
    })
  }, [error, isAuthenticated, isLoading, loginWithRedirect])

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        Loading...
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center p-8">
        <p className="max-w-xl text-center text-red-600">
          Authentication failed: {error.message}
        </p>
      </div>
    )
  }

  if (!isAuthenticated) {
    return null
  }

  return children
}

const router = createBrowserRouter(createRoutesFromElements(
      <>

        <Route path="/login" element={<Login />} />

        <Route path="/callback" element={<AuthCallback />} />

        <Route
          path="/dashboard"
          element={<ProtectedRoute><DashboardPage /></ProtectedRoute>}
        />

        <Route
          path="/users/create"
          element={<ProtectedRoute><CreateUserPage /></ProtectedRoute>}
        />

        <Route path="/rental-bills" element={<ProtectedRoute><RentalBillPage /></ProtectedRoute>} />
        <Route path="/worksites/create" element={<ProtectedRoute><CreateWorksitePage /></ProtectedRoute>} />
        <Route path="/users" element={<ProtectedRoute><DirectoryPage key="users" resource="users" /></ProtectedRoute>} />
        <Route path="/users/:id" element={<ProtectedRoute><DirectoryPage key="user-details" resource="users" /></ProtectedRoute>} />
        <Route path="/worksites" element={<ProtectedRoute><DirectoryPage key="worksites" resource="worksites" /></ProtectedRoute>} />
        <Route path="/worksites/:id" element={<ProtectedRoute><DirectoryPage key="worksite-details" resource="worksites" /></ProtectedRoute>} />

        <Route
          path="/equipment"
          element={<ProtectedRoute><EquipmentPage /></ProtectedRoute>}
        />

        <Route
          path="/equipment/:equipmentCode/history"
          element={<ProtectedRoute><EquipmentHistoryPage /></ProtectedRoute>}
        />

        <Route
          path="/transfers"
          element={<ProtectedRoute><TransferEntryPage /></ProtectedRoute>}
        />

        <Route
          path="/transfers/manage"
          element={<ProtectedRoute><TransferPage /></ProtectedRoute>}
        />

        <Route
          path="/"
          element={<Navigate to="/dashboard" replace />}
        />

      </>
))

export default function App() {
  return <RouterProvider router={router} />
}
