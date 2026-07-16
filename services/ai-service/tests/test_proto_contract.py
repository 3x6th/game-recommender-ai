from app.grpc_server import reco_pb2
from app.tools.grpc_client import tools_pb2


def test_recommendation_proto_keeps_cross_service_field_numbers() -> None:
    request_fields = reco_pb2.FullAiContextRequestProto.DESCRIPTOR.fields_by_name
    response_fields = reco_pb2.RecommendationResponse.DESCRIPTOR.fields_by_name
    history_fields = reco_pb2.ChatHistoryMessageProto.DESCRIPTOR.fields_by_name

    assert request_fields["userMessage"].number == 1
    assert request_fields["requestId"].number == 6
    assert request_fields["maxResults"].number == 10
    assert request_fields["history"].number == 11
    assert history_fields["role"].number == 1
    assert history_fields["text"].number == 2
    assert response_fields["message"].number == 2
    assert response_fields["recommendations"].number == 3
    assert response_fields["provider"].number == 4
    assert response_fields["reasoning"].number == 5


def test_java_tools_proto_exposes_only_the_expected_first_increment() -> None:
    service = tools_pb2.DESCRIPTOR.services_by_name["JavaToolsService"]
    methods = {method.name for method in service.methods}

    assert {"SearchGames", "GetSteamAppDetails"}.issubset(methods)
    assert tools_pb2.SearchGamesRequest.DESCRIPTOR.fields_by_name["limit"].number == 2
    assert tools_pb2.SteamAppResponse.DESCRIPTOR.fields_by_name["genres"].number == 4
