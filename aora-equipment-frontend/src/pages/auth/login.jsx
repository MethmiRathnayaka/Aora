import { useAuth0 } from '@auth0/auth0-react'

export default function Login() {
  const { loginWithRedirect, isLoading } = useAuth0()

  const handleLogin = async () => {
    await loginWithRedirect({
      appState: {
        returnTo: '/dashboard',
      },
    })
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-lg">

        <div className="mb-8 text-center">
          <h1 className="text-3xl font-bold text-gray-900">
            Aora
          </h1>

          <p className="mt-2 text-gray-500">
            Equipment Management System
          </p>
        </div>

        <button
          onClick={handleLogin}
          disabled={isLoading}
          className="w-full rounded-lg bg-black px-4 py-3 font-medium text-white transition hover:bg-gray-800 disabled:opacity-50"
        >
          {isLoading ? 'Loading...' : 'Sign in'}
        </button>

      </div>
    </div>
  )
}
