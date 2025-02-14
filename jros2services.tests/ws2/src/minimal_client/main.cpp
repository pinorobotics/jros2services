// Copyright 2016 Open Source Robotics Foundation, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <chrono>
#include <cinttypes>
#include <memory>
#include <sstream>
#include "example_interfaces/srv/add_two_ints.hpp"
#include "rclcpp/rclcpp.hpp"

using AddTwoInts = example_interfaces::srv::AddTwoInts;
using namespace std;

int main(int argc, char * argv[])
{
  rclcpp::init(argc, argv);
  auto seed = argc > 3? atoi(argv[2]): 41;
  stringstream nodeName;
  nodeName << "minimal_client_" << seed;
  auto node = rclcpp::Node::make_shared(nodeName.str().c_str());
  auto topicName = argc > 2? argv[1]: "add_two_ints";
  auto client = node->create_client<AddTwoInts>(topicName);
  while (!client->wait_for_service(std::chrono::seconds(1))) {
    if (!rclcpp::ok()) {
      RCLCPP_ERROR(node->get_logger(), "client interrupted while waiting for service to appear.");
      return 1;
    }
    RCLCPP_INFO(node->get_logger(), "waiting for service to appear...");
  }
  auto request = std::make_shared<AddTwoInts::Request>();
  request->a = seed;
  for (int i = 1; i <= 10; i++) {
    request->b = i;
    auto result_future = client->async_send_request(request);
    if (rclcpp::spin_until_future_complete(node, result_future) !=
      rclcpp::FutureReturnCode::SUCCESS)
    {
      RCLCPP_ERROR(node->get_logger(), "service call failed :(");
      return 1;
    }
    auto result = result_future.get();
    RCLCPP_INFO(
      node->get_logger(), "result of %" PRId64 " + %" PRId64 " = %" PRId64,
      request->a, request->b, result->sum);
  }
  rclcpp::shutdown();
  return 0;
}
