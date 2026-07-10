/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Emit a self-contained server build so the runtime image stays small.
  output: "standalone",
};

module.exports = nextConfig;
