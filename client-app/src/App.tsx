import { ServiceDiscovery } from "@/components/ServiceDiscovery";
import { EUREKA_URL } from "@/config/eureka";

function App() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 p-8">
      <div className="max-w-6xl mx-auto">
        <div className="mb-6 text-center">
          <h1 className="text-3xl font-bold mb-2">FD Evaluation</h1>
        </div>
        <ServiceDiscovery eurekaUrl={EUREKA_URL} />
      </div>
    </div>
  );
}

export default App;