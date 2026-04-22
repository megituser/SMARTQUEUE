import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',

  // 👇 add this
  allowedDevOrigins: ['http://10.184.94.191:3000'],
};

export default nextConfig;